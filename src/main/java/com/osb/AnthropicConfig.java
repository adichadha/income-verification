package com.osb;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "anthropic")
@Getter
@Setter
public class AnthropicConfig {
    private String apiKey;
    private String baseUrl;
}