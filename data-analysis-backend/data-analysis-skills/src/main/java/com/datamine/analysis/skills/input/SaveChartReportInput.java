package com.datamine.analysis.skills.input;

import java.util.Map;

public record SaveChartReportInput(
        String sql,
        String name,
        Map<String, Object> chartConfig
) {
}
