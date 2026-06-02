package com.datamine.analysis.common.dto.auth;

import java.util.List;

public record UserConnectionAccessDTO(
        Long userId,
        List<Long> connectionIds
) {
}
