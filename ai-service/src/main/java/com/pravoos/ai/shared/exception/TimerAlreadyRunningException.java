package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class TimerAlreadyRunningException extends PravoosException {

    public TimerAlreadyRunningException() {
        super("A time tracking timer is already running", HttpStatus.CONFLICT, "TIMER_ALREADY_RUNNING");
    }
}
