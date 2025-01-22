package com.imd.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, ApiGatewayService apiGatewayService) {
        return builder.routes()
                .route("db-service", r -> r.path("/db-service/**")
                        .filters(f -> f.filter((exchange, chain) -> {
                            return apiGatewayService.callDbService(exchange, chain);
                        }))
                        .uri("lb://db-service"))
                .build();
    }
}

@Service
class ApiGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayService.class);

    @CircuitBreaker(name = "dbServiceCircuitBreaker", fallbackMethod = "fallbackMethod")
    public reactor.core.publisher.Mono<Void> callDbService(org.springframework.web.server.ServerWebExchange exchange,
                                                           org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        logger.info("Chamando o db-service com proteção de CircuitBreaker");
        return chain.filter(exchange); // Continua o fluxo normal se o circuito estiver fechado
    }

    public reactor.core.publisher.Mono<Void> fallbackMethod(org.springframework.web.server.ServerWebExchange exchange,
                                                            org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
                                                            Throwable throwable) {
        logger.error("Fallback ativado devido a erro: {}", throwable.getMessage());
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }
}
