package com.distributed.teamai.account_service.service;


import com.distributed.teamai.account_service.dto.subscription.CheckoutRequest;
import com.distributed.teamai.account_service.dto.subscription.CheckoutResponse;
import com.distributed.teamai.account_service.dto.subscription.PortalResponse;
import com.stripe.model.StripeObject;

import java.util.Map;

public interface PaymentService {

    CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request);

    PortalResponse openCustomerPortal();

    void handleWebhookEvent(String type, StripeObject stripeObject, Map<String, String> metadata);
}
