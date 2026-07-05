package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class UploadRateLimitExceededException extends PravoosException {

    public UploadRateLimitExceededException() {
        super(
            "Слишком много загрузок за короткое время. Подождите немного и повторите попытку.",
            HttpStatus.TOO_MANY_REQUESTS,
            "UPLOAD_RATE_LIMIT_EXCEEDED"
        );
    }
}
