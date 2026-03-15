package com.distributed.teamai.common_lib.security;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@AutoConfiguration
public class SharedSecurityAutoConfiguration {

    @Bean
    public AuthUtils authUtils() {
        return new AuthUtils();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(
            AuthUtils authUtils,

            HandlerExceptionResolver handlerExceptionResolver)
    {
        return new JwtAuthFilter(authUtils, handlerExceptionResolver);
    }

    @Bean
    public RequestInterceptor feignRequestInterceptor(){
        return requestTemplate -> {

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if(authentication != null && authentication.getCredentials() instanceof String token){
                requestTemplate.header("Authorization", "Bearer " + token);
            }
        };
    }

}
