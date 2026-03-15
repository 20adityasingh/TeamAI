package com.distributed.teamai.api_gateway.filter;

import com.distributed.teamai.api_gateway.config.SecurityProperties;
import com.distributed.teamai.api_gateway.service.GatewayAuthService;
import com.distributed.teamai.common_lib.error.ApiError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
@Slf4j
public class GatewayAuthFilter implements GlobalFilter, Ordered {


    private final SecurityProperties securityProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewayAuthService gatewayAuthFilter;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        String path = request.getURI().getPath();

        boolean isPublic = securityProperties.getPublicRoutes()
                .stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path ));

        if(isPublic) {
            log.info("Public route accessed: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            log.info("Unauthorized access attempt to: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            gatewayAuthFilter.validateToken(token);
            log.info("Authorized access to: {}", path);
        } catch (Exception e) {
            log.error("Token validation failed for path: {}. Error: {}", path, e.getMessage());
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        return chain.filter(exchange);

    }

    private Mono<Void> sendErrorResponse(ServerWebExchange exchange, HttpStatus status, String message){
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        ApiError apiError = new ApiError(status, message);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiError);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: {}", e.getMessage());
            return exchange.getResponse().setComplete();
        }

    }


    @Override
    public int getOrder() {
        return 0;
    }


}
