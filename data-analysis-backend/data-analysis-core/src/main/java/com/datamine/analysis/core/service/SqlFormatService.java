package com.datamine.analysis.core.service;

import com.alibaba.druid.sql.SQLUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class SqlFormatService {

    public String format(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "";
        }

        try {
            return SQLUtils.formatMySql(sql).trim();
        } catch (Exception e) {
            log.warn("SQL formatting failed, returning original", e);
            return sql;
        }
    }
}
