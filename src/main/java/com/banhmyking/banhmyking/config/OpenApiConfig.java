package com.banhmyking.banhmyking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bánh Mỳ King API Documentation")
                        .version("1.0")
                        .description("Tài liệu API hệ thống Bánh Mỳ King - Quản lý giỏ hàng và đặt món")
                        .contact(new Contact().name("BanhMyKing Team").email("support@banhmyking.vn")));
    }
}
