package com.trading.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI tradingPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Paper Trading Platform API")
                        .description("""
                                A paper trading platform POC built with Spring Boot.
                                
                                ## Features
                                - Place BUY and SELL market orders
                                - Track portfolio positions and cash balance
                                - View real-time P&L (simulated prices)
                                - Full trade history
                                
                                ## Getting Started
                                1. Use `GET /api/market/price/{symbol}` to check a price
                                2. Use `POST /api/orders` to place a BUY order
                                3. Use `GET /api/portfolio` to see your portfolio
                                4. Use `POST /api/orders` to place a SELL order and realize P&L
                                
                                **Default Portfolio ID: 1** | **Starting Cash: $100,000**
                                """)
                        .version("1.0.0-POC")
                        .contact(new Contact()
                                .name("Trading Platform Team")
                                .email("dev@trading.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
