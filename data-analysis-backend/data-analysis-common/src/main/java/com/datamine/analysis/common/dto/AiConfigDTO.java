package com.datamine.analysis.common.dto;

import lombok.Data;

@Data
public class AiConfigDTO {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private String embeddingModel;
    private Double temperature;
}
