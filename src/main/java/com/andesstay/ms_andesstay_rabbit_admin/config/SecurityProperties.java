package com.andesstay.ms_andesstay_rabbit_admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "andesstay.security")
public record SecurityProperties(String audience, String rolesClaim) {
}
