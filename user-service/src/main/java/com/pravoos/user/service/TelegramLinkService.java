package com.pravoos.user.service;

import com.pravoos.user.config.TelegramProperties;
import com.pravoos.user.shared.exception.InvalidTelegramLinkCodeException;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import com.pravoos.user.model.dto.TelegramLinkResponse;
import com.pravoos.user.identity.internal.model.entity.LawyerProfile;
import com.pravoos.user.model.entity.TelegramLinkCode;
import com.pravoos.user.identity.internal.repository.LawyerProfileRepository;
import com.pravoos.user.repository.TelegramLinkCodeRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class TelegramLinkService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLinkService.class);
    private static final int CODE_BYTES = 9;

    private final TelegramLinkCodeRepository linkCodeRepository;
    private final LawyerProfileRepository lawyerProfileRepository;
    private final TelegramProperties telegramProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder codeEncoder = Base64.getUrlEncoder().withoutPadding();

    public TelegramLinkService(TelegramLinkCodeRepository linkCodeRepository,
                               LawyerProfileRepository lawyerProfileRepository,
                               TelegramProperties telegramProperties) {
        this.linkCodeRepository = linkCodeRepository;
        this.lawyerProfileRepository = lawyerProfileRepository;
        this.telegramProperties = telegramProperties;
    }

    @Transactional
    public TelegramLinkResponse createLinkCode(UUID userId) {
        lawyerProfileRepository.findByUserIdWithUser(userId)
                .orElseThrow(ProfileNotFoundException::new);
        linkCodeRepository.deleteByUserId(userId);
        linkCodeRepository.flush();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plus(telegramProperties.linkCodeTtl());
        String code = generateCode();
        linkCodeRepository.save(new TelegramLinkCode(code, userId, expiresAt, now));

        log.info("Generated Telegram link code for user {}", userId);
        return new TelegramLinkResponse(code, deepLink(code), expiresAt);
    }

    @Transactional
    public String bind(String code, long chatId) {
        TelegramLinkCode linkCode = linkCodeRepository.findById(code)
                .orElseThrow(InvalidTelegramLinkCodeException::new);

        if (linkCode.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            linkCodeRepository.delete(linkCode);
            log.info("Rejected expired Telegram link code for user {}", linkCode.getUserId());
            throw new InvalidTelegramLinkCodeException();
        }

        LawyerProfile profile = lawyerProfileRepository.findByUserIdWithUser(linkCode.getUserId())
                .orElseThrow(ProfileNotFoundException::new);
        profile.setTelegramChatId(chatId);
        linkCodeRepository.delete(linkCode);

        log.info("Bound Telegram chat to user {}", linkCode.getUserId());
        return profile.getFullName();
    }

    @Transactional
    public void unlink(UUID userId) {
        LawyerProfile profile = lawyerProfileRepository.findByUserIdWithUser(userId)
                .orElseThrow(ProfileNotFoundException::new);
        profile.setTelegramChatId(null);
        linkCodeRepository.deleteByUserId(userId);
        log.info("Unlinked Telegram for user {}", userId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveChatId(UUID userId) {
        return lawyerProfileRepository.findByUserIdWithUser(userId)
                .map(LawyerProfile::getTelegramChatId);
    }

    @Scheduled(cron = "0 15 4 * * *")
    @SchedulerLock(name = "TelegramLinkService_purgeExpiredCodes", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredCodes() {
        int removed = linkCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
        if (removed > 0) {
            log.info("Purged {} expired Telegram link codes", removed);
        }
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return codeEncoder.encodeToString(bytes);
    }

    private String deepLink(String code) {
        return "https://t.me/" + telegramProperties.botUsername() + "?start=" + code;
    }
}
