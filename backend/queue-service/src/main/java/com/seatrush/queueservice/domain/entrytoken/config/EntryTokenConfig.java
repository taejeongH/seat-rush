package com.seatrush.queueservice.domain.entrytoken.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EntryTokenProperties.class)
public class EntryTokenConfig {
}
