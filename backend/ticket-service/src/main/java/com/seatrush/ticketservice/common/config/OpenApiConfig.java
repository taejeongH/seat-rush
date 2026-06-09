package com.seatrush.ticketservice.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .info(new Info()
                        .title("Seat Rush Ticket Service API")
                        .description("공연, 좌석, 예매, 인증 기능을 담당하는 Ticket Service API 문서입니다.")
                        .version("v1"))
                .servers(List.of(new Server()
                        .url("http://localhost:8081")
                        .description("Local Ticket Service")));
    }
}
