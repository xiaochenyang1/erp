package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.app")
public record AppProperties(String code, String name, String timezone) {
}
