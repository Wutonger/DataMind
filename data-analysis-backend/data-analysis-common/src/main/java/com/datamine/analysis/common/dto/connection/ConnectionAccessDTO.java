package com.datamine.analysis.common.dto.connection;

import java.util.List;

public record ConnectionAccessDTO(
        Long connectionId,
        List<Long> userIds
) {
}
