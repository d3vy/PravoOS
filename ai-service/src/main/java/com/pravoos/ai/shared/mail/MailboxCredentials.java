package com.pravoos.ai.shared.mail;

public record MailboxCredentials(
    String host, int port, boolean ssl, String username, String password, String folder) {}
