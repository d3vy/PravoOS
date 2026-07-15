package com.pravoos.ai.shared.config;

import com.pravoos.ai.shared.signature.ExternalSignatureProvider;
import com.pravoos.ai.shared.signature.NoopDiadocSignatureProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureConfig {

    private static final Logger log = LoggerFactory.getLogger(SignatureConfig.class);

    @Bean
    public ExternalSignatureProvider diadocSignatureProvider(SignatureProperties properties) {
        if (properties.diadoc().hasKey()) {
            log.warn("DIADOC_API_KEY задан, но клиент Контур.Диадок ещё не реализован — "
                    + "квалифицированная подпись пока недоступна, используется только простая ЭП");
        } else {
            log.info("DIADOC_API_KEY не задан — доступна только простая электронная подпись (простая ЭП в портале)");
        }
        return new NoopDiadocSignatureProvider();
    }
}
