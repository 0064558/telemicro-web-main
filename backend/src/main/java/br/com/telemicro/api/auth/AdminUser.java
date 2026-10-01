package br.com.telemicro.api.auth;

import java.util.UUID;

public record AdminUser(UUID id, String email, String passwordHash, String role, boolean active) {}
