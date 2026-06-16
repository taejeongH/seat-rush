package com.seatrush.virtualuser.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient seatRushWebClient(VirtualUserProperties properties) {
        int timeoutMillis = Math.toIntExact(properties.requestTimeout().toMillis());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis)
                .responseTimeout(properties.requestTimeout())
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS)
                ));

        return WebClient.builder()
                .baseUrl(properties.gatewayBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
