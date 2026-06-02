package com.datamine.analysis.common.dto.auth;

public record UserUpsertRequest(
        String username,
        String password,
        String nickname,
        String role,
        String status
) {
}
