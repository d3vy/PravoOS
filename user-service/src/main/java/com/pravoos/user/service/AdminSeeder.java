package com.pravoos.user.service;

import com.pravoos.user.config.AdminProperties;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AdminProperties adminProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminProperties.accounts() == null || adminProperties.accounts().isEmpty()) {
            log.warn("No admin accounts configured (ADMIN_EMAIL/ADMIN_PASSWORD); skipping admin seeding");
            return;
        }
        adminProperties.accounts().forEach(this::seedAdmin);
    }

    private void seedAdmin(AdminProperties.Account account) {
        if (isBlank(account.email()) || isBlank(account.password())) {
            return;
        }

        String email = EmailNormalizer.normalize(account.email());
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(account.password()));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        log.info("Admin account created: {}", email);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
