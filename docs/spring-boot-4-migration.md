# Миграция на Spring Boot 4

Статус: решение принято, работа не начата.
Текущее состояние: Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 21.

## Зачем

### 1. Мы больше не можем получить секьюрити-патч. Вообще

Ветка Spring Boot 3.3 снята с OSS-поддержки 30 июня 2025 года. К августу 2026 закрыты **все** ветки 3.x — открытые патчи выходят только для 4.0 и 4.1.

Практическое следствие: когда в Spring найдут следующую RCE или authorization bypass, фикс выйдет в 4.0.x/4.1.x и **не будет бэкпортирован** в 3.3. У нас останется три варианта, все плохие:

- экстренная миграция на 4.x под давлением активно эксплуатируемой уязвимости, без времени на тесты;
- коммерческая расширенная поддержка (HeroDevs/TuxCare) — деньги за то, что бесплатно доступно на поддерживаемой ветке;
- жить с дырой.

Это не «есть известная CVE, надо обновиться». Известных CVE, бьющих по нашей конфигурации, сейчас нет — я проверял свежие actuator-уязвимости (CVE-2026-40976, CVE-2026-22731, CVE-2026-22733), под них проект не подпадает. Проблема в другом: **у нас сломан сам механизм получения исправлений**. Dependabot настроен на maven, но внутри 3.3.x он ничего не предложит, потому что релизов 3.3.x больше не выпускают. Мониторинг зависимостей создаёт ложное ощущение защищённости.

Для B2B-продукта, который хранит ПДн и адвокатскую тайну, «мы на фреймворке без поддержки» — это ещё и вопрос, который прилетит в первом же security-опроснике от корпоративного клиента.

### 2. Цена растёт со временем, а не падает

Spring Boot не назначает LTS-релизов: каждая минорная версия живёт 12 месяцев, новые минорные выходят раз в полгода. Мы уже отстали на две мажорных ветки. Каждый месяц ожидания добавляет ещё один слой изменений, который придётся проходить разом.

### 3. Что мы получаем помимо патчей

- **Модульная структура стартеров** — меньше транзитивных зависимостей в рантайме, то есть меньше площадь атаки и меньше шума в SCA-сканах.
- **JSpecify null-safety** по всему API фреймворка — статически ловятся NPE-классы ошибок, которые сейчас всплывают в рантайме.
- Spring Framework 7, Jakarta EE 11, поддержка Java 25 (baseline остаётся 17, наши 21 подходят).

Это приятные, но вторичные аргументы. Мигрируем ради пункта 1.

## Целевые версии

| Компонент     | Сейчас      | Цель                                      |
|---------------|-------------|-------------------------------------------|
| Spring Boot   | 3.3.5       | 4.1.x (4.0 EOL 31.12.2026 — брать смысла нет) |
| Spring Cloud  | 2023.0.3    | 2025.1.2+ (Oakwood; совместим с 4.0.7 и 4.1.0) |
| Java          | 21          | 21 (менять не требуется)                  |

Прыжок через 3.4/3.5 напрямую в 4.1 — правильный выбор: промежуточные ветки тоже EOL, а проходить их поэтапно означает дважды платить за одни и те же удалённые API.

## Что конкретно сломается у нас

Проверил по коду, а не по общему списку из гайда.

**Точно затрагивает нас:**

1. **Jackson 2 → Jackson 3.** Меняется groupId (`com.fasterxml.jackson` → `tools.jackson`), переименованы классы (`Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`), JSON-свойства переехали из `spring.jackson.read.*` в `spring.jackson.json.read.*`. У нас 82 файла с прямыми импортами `com.fasterxml.jackson` — самый объёмный пункт. Аннотации (`@JsonProperty`, `@JsonAlias`, `@JsonIgnoreProperties`) правятся механически, ручной работы требуют места с `ObjectMapper`, `TypeReference`, `JavaTimeModule`.

2. **`spring-boot-starter-web` → `spring-boot-starter-webmvc`.** 5 pom-файлов.

3. **Flyway требует явного стартера** `spring-boot-starter-flyway` вместо голого `flyway-core` (`ai-service/pom.xml:115`).

4. **Spring Cloud Gateway: переименование артефактов.** `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`, плюс новые префиксы свойств. Старые имена в 2025.x ещё работают с warning в логах, но переезжать надо сразу. Затрагивает `api-gateway`.

5. **Spring Security 7.0** — свои breaking changes в дефолтах, требуют отдельного прохода по `SecurityConfig` во всех сервисах. Здесь же ловим риск тихой поломки: часть изменений в дефолтах не падает на старте, а меняет поведение REST-эндпоинтов. Нужны интеграционные тесты на каждый защищённый маршрут **до** миграции.

6. **Удалено всё, что было deprecated в 3.x.** Компилятор покажет.

**Нас не затрагивает (проверено):**

- Undertow не используется — мы на Tomcat.
- JUnit 4 в проекте отсутствует (0 файлов).
- `@MockBean`/`@SpyBean` не используются (0 файлов) — переход на `@MockitoBean` не нужен.
- MongoDB в конфигурации не поднята — переименование `spring.data.mongodb` → `spring.mongodb` неактуально.

## Как делать

Порядок важен: сначала страховка, потом изменения.

1. **Поднять покрытие интеграционными тестами на security-маршруты и сериализацию.** Сейчас порог JaCoCo стоит на 0.01 (`pom.xml`) — фактически гейта нет. Мигрировать Spring Security 7 без тестов на каждый защищённый эндпоинт нельзя: поломки будут тихими.
2. **Прогнать OpenRewrite-рецепты** (`UpgradeSpringBoot_4_0`, `UpgradeSpringCloud_2025_1`, `SpringCloudGatewayDeprecatedModulesAndStarters`) — они снимают механическую часть: переименования артефактов, импортов Jackson, префиксов свойств.
3. **По одному модулю за раз**, начиная с наименее связанных: `discovery-server` → `config-server` → `notification-service` → `llm-service` → `user-service` → `ai-service` → `api-gateway` последним (у него самая специфичная конфигурация).
4. **Спуститься по веткам не получится** — Spring Cloud 2025.1 требует Boot 4.x, так что Boot и Cloud поднимаются одним коммитом на модуль.
5. **После каждого модуля** — `mvn verify` целиком, включая Modulith-гейт.

## Источники

- [Spring Boot 4.0 Migration Guide (официальный)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot End of Life: Every 3.x Branch Is Now Unsupported](https://www.danvega.dev/blog/spring-boot-end-of-life)
- [Spring Boot Versions, EOL Dates, and Latest Releases](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026)
- [Spring Cloud 2025.1.2 (Oakwood) release notes](https://spring.io/blog/2026/06/11/spring-cloud-2025-1-2-aka-oakwood-has-been-released/)
- [Spring Cloud Gateway: новые модули и стартеры](https://docs.openrewrite.org/recipes/java/spring/cloud2025/springcloudgatewaydeprecatedmodulesandstarters)
- [Spring Boot 4 & Spring Framework 7 – What's New](https://www.baeldung.com/spring-boot-4-spring-framework-7)
