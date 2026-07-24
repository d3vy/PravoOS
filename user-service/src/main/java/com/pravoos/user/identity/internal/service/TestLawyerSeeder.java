package com.pravoos.user.identity.internal.service;

import com.pravoos.common.util.PhoneNormalizer;
import com.pravoos.user.identity.internal.config.TestLawyerProperties;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TestLawyerSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TestLawyerSeeder.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TestLawyerProperties properties;

  public TestLawyerSeeder(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TestLawyerProperties properties) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) {
      return;
    }
    if (isBlank(properties.email()) || isBlank(properties.password())) {
      log.warn(
          "Test lawyer seeding enabled but TEST_LAWYER_EMAIL/TEST_LAWYER_PASSWORD are blank; skipping");
      return;
    }

    String email = EmailNormalizer.normalize(properties.email());
    if (userRepository.existsByEmail(email)) {
      return;
    }

    User lawyer = new User();
    lawyer.setEmail(email);
    lawyer.setPasswordHash(passwordEncoder.encode(properties.password()));
    lawyer.setRole(UserRole.LAWYER);
    lawyer.setStatus(UserStatus.ACTIVE);

    LawyerProfile profile = new LawyerProfile();
    profile.setFullName(properties.fullName());
    profile.setSpecialization(properties.specialization());
    profile.setPhone(PhoneNormalizer.normalize(properties.phone()));
    profile.setUser(lawyer);
    lawyer.setLawyerProfile(profile);

    userRepository.save(lawyer);
    log.info("Test lawyer account created: {}", EmailMasker.mask(email));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
