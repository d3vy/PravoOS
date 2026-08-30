package com.pravoos.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiWriteTool;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.llm.api.ToolSpec;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiToolCatalogTest {

  private static final String BASE_PACKAGE = "com.pravoos.ai";
  private static final Pattern TOOL_NAME = Pattern.compile("^[a-z][a-z0-9_]*$");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static final Path LOCALE_EN =
      Path.of("..", "frontend", "src", "i18n", "locales", "en.ts");
  private static final Path LOCALE_RU =
      Path.of("..", "frontend", "src", "i18n", "locales", "ru.ts");
  private static final Pattern TOOL_STEP_BLOCK =
      Pattern.compile("toolStep:\\s*\\{([^}]*)\\}", Pattern.DOTALL);
  private static final Pattern TOOL_STEP_KEY = Pattern.compile("([a-z][a-z0-9_]*):\\s*'");
  private static final String I18N_ONLY_KEY = "error";

  private static final Set<String> ACTOR_SCOPED_PARAMETERS =
      Set.of(
          "orgid",
          "orgids",
          "organizationid",
          "tenantid",
          "lawyerid",
          "userid",
          "actorid",
          "ownerid",
          "role");

  private static final List<String> EXPECTED_TOOL_NAMES =
      List.of(
          "add_deadline",
          "approve_pending_actions",
          "archive_case",
          "archive_client",
          "cancel_pending_actions",
          "create_case",
          "create_client",
          "create_invoice",
          "create_task",
          "get_case",
          "get_client",
          "link_document_to_case",
          "read_document",
          "search_workspace");

  private final List<AiTool> tools = instantiateEveryTool();

  @Test
  void everyToolOnTheClasspathIsAccountedFor() {
    assertThat(tools)
        .extracting(AiTool::name)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
  }

  @Test
  void theRegistryAcceptsTheWholeCatalogWithoutAClashOfNames() {
    AiToolRegistry registry = new AiToolRegistry(tools);

    List<ToolSpec> specs = registry.specsFor(lawyer());

    assertThat(specs).extracting(ToolSpec::name).doesNotHaveDuplicates().isNotEmpty();
  }

  @Test
  void everyToolNameIsAnIdentifierTheModelCanCallBack() {
    assertThat(tools).allSatisfy(tool -> assertThat(tool.name()).matches(TOOL_NAME));
  }

  @Test
  void everyToolExplainsItselfToTheModel() {
    assertThat(tools)
        .allSatisfy(tool -> assertThat(tool.description()).isNotBlank().hasSizeGreaterThan(40));
  }

  @Test
  void everyToolDeclaresAClosedObjectSchema() {
    assertThat(tools)
        .allSatisfy(
            tool -> {
              JsonNode schema = tool.parameters();
              assertThat(schema.path("type").asText()).isEqualTo("object");
              assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
              JsonNode properties = schema.path("properties");
              assertThat(properties.isObject()).isTrue();
              schema
                  .path("required")
                  .forEach(field -> assertThat(properties.has(field.asText())).isTrue());
              properties
                  .fields()
                  .forEachRemaining(
                      property -> {
                        assertThat(property.getValue().path("type").asText()).isNotBlank();
                        assertThat(property.getValue().path("description").asText()).isNotBlank();
                      });
            });
  }

  @Test
  void noToolLetsTheModelChooseTheTenantOrTheActor() {
    assertThat(tools)
        .allSatisfy(
            tool ->
                tool.parameters()
                    .path("properties")
                    .fieldNames()
                    .forEachRemaining(
                        property ->
                            assertThat(property.toLowerCase(Locale.ROOT))
                                .as("tool %s exposes %s to the model", tool.name(), property)
                                .isNotIn(ACTOR_SCOPED_PARAMETERS)));
  }

  @Test
  void everyWriteToolIsExcludedFromDeduplicationAndOfferedToLawyersOnly() {
    assertThat(tools)
        .filteredOn(AiWriteTool.class::isInstance)
        .isNotEmpty()
        .allSatisfy(
            tool -> {
              assertThat(tool.readOnly()).isFalse();
              assertThat(tool.availableFor(lawyer())).isTrue();
              assertThat(tool.availableFor(admin())).isFalse();
            });
  }

  @Test
  void anAdminIsNeverOfferedAToolThatReachesIntoALawyerWorkspace() {
    AiToolRegistry registry = new AiToolRegistry(tools);

    assertThat(registry.specsFor(admin())).isEmpty();
  }

  @Test
  void everyToolHasAnI18nLabelInBothLocales() {
    assertThat(toolStepKeysFrom(LOCALE_EN))
        .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    assertThat(toolStepKeysFrom(LOCALE_RU))
        .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
  }

  private static Set<String> toolStepKeysFrom(Path localeFile) {
    String content;
    try {
      content = Files.readString(localeFile);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not read locale file: " + localeFile, ex);
    }
    Matcher blockMatcher = TOOL_STEP_BLOCK.matcher(content);
    if (!blockMatcher.find()) {
      throw new IllegalStateException("No chat.toolStep block found in " + localeFile);
    }
    Set<String> keys = new HashSet<>();
    Matcher keyMatcher = TOOL_STEP_KEY.matcher(blockMatcher.group(1));
    while (keyMatcher.find()) {
      keys.add(keyMatcher.group(1));
    }
    keys.remove(I18N_ONLY_KEY);
    return keys;
  }

  private static AiToolContext lawyer() {
    return new AiToolContext(
        UUID.randomUUID(),
        List.of(UUID.randomUUID()),
        AiActorRole.LAWYER,
        "conv-1",
        LocalDateTime.now());
  }

  private static AiToolContext admin() {
    return new AiToolContext(
        UUID.randomUUID(), List.of(), AiActorRole.ADMIN, "conv-1", LocalDateTime.now());
  }

  private static List<AiTool> instantiateEveryTool() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(AiTool.class));
    return scanner.findCandidateComponents(BASE_PACKAGE).stream()
        .map(BeanDefinition::getBeanClassName)
        .map(AiToolCatalogTest::loadClass)
        .filter(AiTool.class::isAssignableFrom)
        .filter(AiToolCatalogTest::isProductionClass)
        .map(AiToolCatalogTest::instantiate)
        .toList();
  }

  private static boolean isProductionClass(Class<?> toolClass) {
    CodeSource codeSource = toolClass.getProtectionDomain().getCodeSource();
    return codeSource != null && !codeSource.getLocation().getPath().endsWith("test-classes/");
  }

  private static Class<?> loadClass(String className) {
    try {
      return ClassUtils.forName(className, AiToolCatalogTest.class.getClassLoader());
    } catch (ClassNotFoundException ex) {
      throw new IllegalStateException(
          "AI tool class disappeared from the classpath: " + className, ex);
    }
  }

  private static AiTool instantiate(Class<?> toolClass) {
    Constructor<?> constructor = toolClass.getDeclaredConstructors()[0];
    Object[] arguments =
        java.util.Arrays.stream(constructor.getParameters())
            .map(AiToolCatalogTest::collaboratorFor)
            .toArray();
    try {
      constructor.setAccessible(true);
      return (AiTool) constructor.newInstance(arguments);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(
          "AI tool " + toolClass.getName() + " is not constructible", ex);
    }
  }

  private static Object collaboratorFor(Parameter parameter) {
    Class<?> type = parameter.getType();
    if (type == ObjectMapper.class) {
      return MAPPER;
    }
    if (type == Validator.class) {
      return VALIDATOR;
    }
    if (type == int.class) {
      return 12000;
    }
    if (type == long.class) {
      return 12000L;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == String.class) {
      return "";
    }
    return Mockito.mock(type);
  }
}
