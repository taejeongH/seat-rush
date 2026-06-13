package com.seatrush.ticketservice.common.config;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenInterceptor;
import com.seatrush.ticketservice.common.log.LoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoggingInterceptor loggingInterceptor;
    private final EntryTokenInterceptor entryTokenInterceptor;

    public WebConfig(
            LoggingInterceptor loggingInterceptor,
            EntryTokenInterceptor entryTokenInterceptor
    ) {
        this.loggingInterceptor = loggingInterceptor;
        this.entryTokenInterceptor = entryTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor);
        registry.addInterceptor(entryTokenInterceptor);
    }
}
