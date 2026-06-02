package com.datamine.analysis.common.dto.auth;

public record AuthLoginResponse(
        String token,
        UserProfileDTO user
) {
}
