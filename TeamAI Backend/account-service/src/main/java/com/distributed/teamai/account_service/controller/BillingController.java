package com.distributed.teamai.account_service.controller;

import com.distributed.teamai.account_service.dto.subscription.*;
import com.distributed.teamai.account_service.service.PaymentService;
import com.distributed.teamai.account_service.service.SubscriptionService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BillingController {

    SubscriptionService subscriptionService;
    PaymentService paymentService;

    @Value("${stripe.webhooks.secret}")
    @NonFinal
    private String webhookSecret;

    @GetMapping("/me/subscription")
    public ResponseEntity<SubscriptionResponse> getMySubscription() {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription());
    }

    @PostMapping("/api/payment/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutResponse(
            @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(paymentService.createCheckoutSessionUrl(request));
    }

    @PostMapping("/api/payment/portal")
    public ResponseEntity<PortalResponse> openCustomerPortal() {
        return ResponseEntity.ok(paymentService.openCustomerPortal());
    }

    @PostMapping("/webhooks/payment")
    public ResponseEntity<String> handlePaymentWebhooks(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) throws SignatureVerificationException {

        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;

        if (deserializer.getObject().isPresent()) {
            stripeObject = deserializer.getObject().get();
        } else {

            try {
                stripeObject = deserializer.deserializeUnsafe();
                if (stripeObject == null) {
                    log.warn("Failed to deserialize webhook object for event: {}", event.getType());
                    return ResponseEntity.ok().build();
                }
            } catch (Exception e) {
                log.error("Unsafe deserialization failed for event {}: {}", event.getType(), e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Deserialization failed");
            }
        }

        Map<String, String> metadata = new HashMap<>();

        if (stripeObject instanceof Session session) {
            metadata = session.getMetadata();
        }

        paymentService.handleWebhookEvent(event.getType(), stripeObject, metadata);

        return ResponseEntity.ok("Webhook Handled");
    }
}
