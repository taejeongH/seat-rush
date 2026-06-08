package com.seatrush.paymentservice.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Seat Rush Payment Service API")
                        .description("Mock 결제 요청과 결제 결과 이벤트 발행을 담당하는 Payment Service API 문서입니다.")
                        .version("v1"))
                .servers(List.of(new Server()
                        .url("http://localhost:8083")
                        .description("Local Payment Service")));
    }
}
