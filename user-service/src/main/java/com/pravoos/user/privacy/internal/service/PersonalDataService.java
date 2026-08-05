package com.pravoos.user.privacy.internal.service;

import com.pravoos.user.identity.api.UserSessionQuery;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.privacy.api.AccountEraser;
import com.pravoos.user.privacy.internal.config.PrivacyProperties;
import com.pravoos.user.privacy.internal.dto.PersonalDataExportResponse;
import com.pravoos.user.privacy.internal.dto.SubjectRequestResponse;
import com.pravoos.user.privacy.internal.model.entity.SubjectRequest;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestStatus;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestType;
import com.pravoos.user.privacy.internal.repository.SubjectRequestRepository;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import com.pravoos.user.shared.exception.SubjectNotFoundException;
import com.pravoos.user.shared.util.EmailMasker;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalDataService {

  private static final Logger log = LoggerFactory.getLogger(PersonalDataService.class);

  private final UserRepository userRepository;
  private final UserSessionQuery userSessionQuery;
  private final ConsentService consentService;
  private final SubjectRequestRepository subjectRequestRepository;
  private final PasswordEncoder passwordEncoder;
  private final PrivacyProperties privacyProperties;
  private final List<AccountEraser> accountErasers;

  public PersonalDataService(
      UserRepository userRepository,
      UserSessionQuery userSessionQuery,
      ConsentService consentService,
      SubjectRequestRepository subjectRequestRepository,
      PasswordEncoder passwordEncoder,
      PrivacyProperties privacyProperties,
      List<AccountEraser> accountErasers) {
    this.userRepository = userRepository;
    this.userSessionQuery = userSessionQuery;
    this.consentService = consentService;
    this.subjectRequestRepository = subjectRequestRepository;
    this.passwordEncoder = passwordEncoder;
    this.privacyProperties = privacyProperties;
    this.accountErasers = accountErasers;
  }

  @Transactional
  public PersonalDataExportResponse export(UUID userId, String ipAddress) {
    User user = userRepository.findById(userId).orElseThrow(SubjectNotFoundException::new);
    LawyerProfile profile = user.getLawyerProfile();

    SubjectRequest request =
        record(
            user, SubjectRequestType.ACCESS, ipAddress, privacyProperties.resolvedAccessDueDays());
    request.complete("Выгрузка сформирована автоматически");

    List<PersonalDataExportResponse.SessionRecord> sessions =
        userSessionQuery.activeSessions(userId).stream()
            .map(
                session ->
                    new PersonalDataExportResponse.SessionRecord(
                        session.ipAddress(),
                        session.userAgent(),
                        session.createdAt(),
                        session.lastUsedAt()))
            .toList();

    log.info("Personal data export produced for user {}", userId);
    return new PersonalDataExportResponse(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getStatus().name(),
        user.getCreatedAt(),
        profile == null ? null : profile.getFullName(),
        profile == null ? null : profile.getSpecialization(),
        profile == null ? null : profile.getPhone(),
        user.getPreferredLanguage(),
        profile != null && profile.getTelegramChatId() != null,
        consentService.list(userId),
        sessions,
        subjectRequestRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
            .map(SubjectRequestResponse::from)
            .toList(),
        privacyProperties.resolvedOperatorName(),
        LocalDateTime.now());
  }

  @Transactional
  public SubjectRequestResponse requestErasure(UUID userId, String rawPassword, String ipAddress) {
    User user = userRepository.findById(userId).orElseThrow(SubjectNotFoundException::new);
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    SubjectRequest request =
        record(
            user,
            SubjectRequestType.ERASURE,
            ipAddress,
            privacyProperties.resolvedErasureDueDays());

    Optional<AccountEraser> eraser =
        accountErasers.stream().filter(candidate -> candidate.supports(userId)).findFirst();
    if (eraser.isPresent()) {
      eraser.get().erase(userId);
      request.complete("Аккаунт и связанные персональные данные удалены");
      log.warn(
          "Account {} ({}) erased on subject request", userId, EmailMasker.mask(user.getEmail()));
    } else {
      log.warn(
          "Erasure request {} for user {} queued for manual processing", request.getId(), userId);
    }
    return SubjectRequestResponse.from(subjectRequestRepository.save(request));
  }

  @Transactional(readOnly = true)
  public List<SubjectRequestResponse> myRequests(UUID userId) {
    return subjectRequestRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
        .map(SubjectRequestResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SubjectRequestResponse> pendingRequests() {
    return subjectRequestRepository
        .findByStatusOrderByDueAtAsc(SubjectRequestStatus.PENDING)
        .stream()
        .map(SubjectRequestResponse::from)
        .toList();
  }

  @Transactional
  public SubjectRequestResponse completeRequest(UUID requestId, String note) {
    SubjectRequest request =
        subjectRequestRepository.findById(requestId).orElseThrow(SubjectNotFoundException::new);
    request.complete(note == null || note.isBlank() ? "Обработано вручную" : note);
    return SubjectRequestResponse.from(request);
  }

  private SubjectRequest record(User user, SubjectRequestType type, String ipAddress, int dueDays) {
    SubjectRequest request = new SubjectRequest();
    request.setUserId(user.getId());
    request.setSubjectRef(EmailMasker.mask(user.getEmail()));
    request.setType(type);
    request.setIpAddress(ipAddress);
    request.setDueAt(LocalDateTime.now().plusDays(dueDays));
    return subjectRequestRepository.save(request);
  }
}
