package com.datamine.analysis.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponseDTO<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {

    public static <T> PageResponseDTO<T> from(Page<T> pageData) {
        return new PageResponseDTO<>(
                pageData.getContent(),
                pageData.getTotalElements(),
                pageData.getNumber() + 1,
                pageData.getSize(),
                pageData.getTotalPages()
        );
    }
}
