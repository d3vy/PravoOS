package com.pravoos.user;

import com.pravoos.user.config.AdminProperties;
import com.pravoos.user.config.BruteForceProperties;
import com.pravoos.user.config.InternalSecretProperties;
import com.pravoos.user.config.JwtProperties;
import com.pravoos.user.config.RefreshCookieProperties;
import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.config.TelegramProperties;
import com.pravoos.user.config.TestLawyerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({JwtProperties.class, AdminProperties.class, RefreshCookieProperties.class, BruteForceProperties.class, InternalSecretProperties.class, ResendProperties.class, TestLawyerProperties.class, TelegramProperties.class})
@EnableScheduling
@EnableAsync
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
