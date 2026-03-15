package com.distributed.teamai.account_service.config;


import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {


    @Value("${stripe.api.secret}")
    private String stripeSecretKey;

    @PostConstruct
    public void config(){
        Stripe.apiKey = stripeSecretKey;
    }
}
