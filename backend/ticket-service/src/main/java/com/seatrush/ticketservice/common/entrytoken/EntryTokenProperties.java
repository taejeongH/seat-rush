package com.seatrush.ticketservice.common.entrytoken;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "entry-token")
public record EntryTokenProperties(
        String issuer,
        String audience,
        Resource publicKeyLocation
) {
}
