package com.datamine.analysis.common.dto.auth;

public record AuthLoginRequest(
        String username,
        String password
) {
}
