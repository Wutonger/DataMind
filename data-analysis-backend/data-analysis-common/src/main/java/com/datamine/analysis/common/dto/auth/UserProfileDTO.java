package com.datamine.analysis.common.dto.auth;

public record UserProfileDTO(
        Long id,
        String username,
        String nickname,
        String role,
        String status,
        Long lastConnectionId
) {
}
