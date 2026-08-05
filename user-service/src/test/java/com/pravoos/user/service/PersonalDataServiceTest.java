package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pravoos.user.identity.api.UserSessionQuery;
import com.pravoos.user.identity.api.UserSessionSnapshot;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.privacy.api.AccountEraser;
import com.pravoos.user.privacy.internal.config.PrivacyProperties;
import com.pravoos.user.privacy.internal.model.entity.SubjectRequest;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestStatus;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestType;
import com.pravoos.user.privacy.internal.repository.SubjectRequestRepository;
import com.pravoos.user.privacy.internal.service.ConsentService;
import com.pravoos.user.privacy.internal.service.PersonalDataService;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PersonalDataServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String PASSWORD = "Sup3rSecret";

  @Mock private UserRepository userRepository;
  @Mock private UserSessionQuery userSessionQuery;
  @Mock private ConsentService consentService;
  @Mock private SubjectRequestRepository subjectRequestRepository;
  @Mock private AccountEraser accountEraser;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

  private PersonalDataService service(AccountEraser... erasers) {
    return new PersonalDataService(
        userRepository,
        userSessionQuery,
        consentService,
        subjectRequestRepository,
        passwordEncoder,
        new PrivacyProperties("1.0", "ООО «Право»", 10, 30),
        List.of(erasers));
  }

  @BeforeEach
  void setUp() {
    lenient().when(subjectRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void export_returnsProfileConsentsAndSessions() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
    when(userSessionQuery.activeSessions(USER_ID))
        .thenReturn(
            List.of(new UserSessionSnapshot("1.2.3.4", "Chrome", LocalDateTime.now(), null)));
    when(consentService.list(USER_ID)).thenReturn(List.of());
    when(subjectRequestRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
        .thenReturn(List.of());

    var export = service().export(USER_ID, "1.2.3.4");

    assertThat(export.email()).isEqualTo("lawyer@example.com");
    assertThat(export.fullName()).isEqualTo("Иванов Иван Иванович");
    assertThat(export.sessions()).hasSize(1);
    assertThat(export.operator()).isEqualTo("ООО «Право»");
  }

  @Test
  void export_isLoggedAsCompletedAccessRequest() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
    when(userSessionQuery.activeSessions(USER_ID)).thenReturn(List.of());
    when(consentService.list(USER_ID)).thenReturn(List.of());
    when(subjectRequestRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
        .thenReturn(List.of());

    service().export(USER_ID, "1.2.3.4");

    ArgumentCaptor<SubjectRequest> captor = ArgumentCaptor.forClass(SubjectRequest.class);
    verify(subjectRequestRepository).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(SubjectRequestType.ACCESS);
    assertThat(captor.getValue().getStatus()).isEqualTo(SubjectRequestStatus.COMPLETED);
    assertThat(captor.getValue().getSubjectRef()).doesNotContain("lawyer@example.com");
  }

  @Test
  void requestErasure_rejectsWrongPassword() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

    assertThatThrownBy(() -> service().requestErasure(USER_ID, "wrong", "1.2.3.4"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(subjectRequestRepository, never()).save(any());
  }

  @Test
  void requestErasure_erasesImmediatelyWhenEraserSupportsSubject() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
    when(accountEraser.supports(USER_ID)).thenReturn(true);

    var response = service(accountEraser).requestErasure(USER_ID, PASSWORD, "1.2.3.4");

    verify(accountEraser).erase(USER_ID);
    assertThat(response.status()).isEqualTo(SubjectRequestStatus.COMPLETED);
  }

  @Test
  void requestErasure_queuesRequestWhenNoEraserSupportsSubject() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
    when(accountEraser.supports(USER_ID)).thenReturn(false);

    var response = service(accountEraser).requestErasure(USER_ID, PASSWORD, "1.2.3.4");

    verify(accountEraser, never()).erase(any());
    assertThat(response.status()).isEqualTo(SubjectRequestStatus.PENDING);
    assertThat(response.dueAt()).isAfter(LocalDateTime.now().plusDays(29));
  }

  @Test
  void completeRequest_marksPendingRequestDone() {
    SubjectRequest request = new SubjectRequest();
    request.setUserId(USER_ID);
    request.setType(SubjectRequestType.ERASURE);
    request.setDueAt(LocalDateTime.now().plusDays(30));
    UUID requestId = UUID.randomUUID();
    when(subjectRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

    var response = service().completeRequest(requestId, "Удалено вручную");

    assertThat(response.status()).isEqualTo(SubjectRequestStatus.COMPLETED);
    assertThat(response.note()).isEqualTo("Удалено вручную");
  }

  private User user() {
    User user = new User();
    user.setId(USER_ID);
    user.setEmail("lawyer@example.com");
    user.setPasswordHash(passwordEncoder.encode(PASSWORD));
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    LawyerProfile profile = new LawyerProfile();
    profile.setFullName("Иванов Иван Иванович");
    profile.setPhone("+79990000000");
    profile.setSpecialization("Арбитраж");
    user.setLawyerProfile(profile);
    return user;
  }
}
