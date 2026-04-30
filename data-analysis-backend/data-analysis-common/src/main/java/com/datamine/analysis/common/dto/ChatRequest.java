package com.datamine.analysis.common.dto;

import lombok.Data;

@Data
public class ChatRequest {

    private String sessionId;

    private Long connectionId;

    private String message;
}
