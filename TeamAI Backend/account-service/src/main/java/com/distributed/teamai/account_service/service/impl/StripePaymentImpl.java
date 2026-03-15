package com.distributed.teamai.account_service.service.impl;

import com.distributed.teamai.account_service.dto.subscription.CheckoutRequest;
import com.distributed.teamai.account_service.dto.subscription.CheckoutResponse;
import com.distributed.teamai.account_service.dto.subscription.PortalResponse;
import com.distributed.teamai.account_service.entity.Plan;
import com.distributed.teamai.account_service.entity.User;
import com.distributed.teamai.common_lib.enums.SubscriptionStatus;
import com.distributed.teamai.common_lib.error.BadRequestException;
import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import com.distributed.teamai.account_service.repository.PlanRepository;
import com.distributed.teamai.account_service.repository.UserRepository;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.account_service.service.PaymentService;
import com.distributed.teamai.account_service.service.SubscriptionService;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StripePaymentImpl implements PaymentService {

    AuthUtils authUtils;
    PlanRepository planRepository;
    UserRepository userRepository;
    SubscriptionService subscriptionService;

    @Value("${app.frontend.url:http://localhost:3000}")
    @NonFinal
    private String frontEnd;

    @Override
    public CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request) {

        Plan plan = planRepository.findById(request.planId()).orElseThrow(
                () -> new ResourceNotFoundException("Plan", request.planId().toString()));

        Long userId = authUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user", userId.toString()));

        var params = SessionCreateParams.builder()
                .addLineItem(
                        SessionCreateParams.LineItem.builder().setPrice(plan.getStripePriceId()).setQuantity(1L)
                                .build())
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .setBillingMode(SessionCreateParams.SubscriptionData.BillingMode.builder()
                                        .setType(SessionCreateParams.SubscriptionData.BillingMode.Type.FLEXIBLE)
                                        .build())
                                .build()

                )
                .setSuccessUrl(frontEnd + "/success.html?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontEnd + "/cancel.html")
                .putMetadata("userId", userId.toString())
                .putMetadata("planId", plan.getId().toString());
        try {

            String stripeCustomerId = user.getStripeCustomerId();

            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                params.setCustomerEmail(user.getUsername());
            } else {
                params.setCustomer(stripeCustomerId);
            }

            Session session = Session.create(params.build());

            return new CheckoutResponse(session.getUrl());
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public PortalResponse openCustomerPortal() {

        Long userId = authUtils.getCurrentUserId();

        User user = getUser(userId);

        String customerId = user.getStripeCustomerId();

        if (customerId == null || customerId.isEmpty()) {
            throw new BadRequestException("Stripe Customer ID does not exist for User ID " + userId);
        }

        try {
            var portalSession = com.stripe.model.billingportal.Session.create(
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .setReturnUrl(frontEnd)
                            .build());

            return new PortalResponse(portalSession.getUrl());
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleWebhookEvent(String type, StripeObject stripeObject, Map<String, String> metadata) {

        log.info("Handling Stripe Events {}", type);

        switch (type) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted((Session) stripeObject, metadata);
            case "customer.subscription.updated" -> handleCustomerSubscriptionUpdated((Subscription) stripeObject);
            case "customer.subscription.deleted" -> handleCustomerSubscriptionDeleted((Subscription) stripeObject);
            case "invoice.paid" -> handleInvoicePaid((Invoice) stripeObject);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed((Invoice) stripeObject);
            default -> log.debug("Ignoring the event: {}", type);
        }

    }

    private void handleCheckoutSessionCompleted(Session session, Map<String, String> metadata) {

        if (session == null) {
            log.error("Session Object was null inside handleCheckoutSessionCompleted");
            return;
        }

        Long userId = Long.parseLong(metadata.get("userId"));
        Long planId = Long.parseLong(metadata.get("planId"));

        User user = getUser(userId);

        String subscriptionId = session.getSubscription();
        String customerId = session.getCustomer();

        if (user.getStripeCustomerId() == null) {
            user.setStripeCustomerId(customerId);
            userRepository.save(user);
        }

        subscriptionService.activateSubscription(userId, planId, subscriptionId, customerId);

    }

    private void handleCustomerSubscriptionUpdated(Subscription subscription) {
        if (subscription == null) {
            log.error("Subscription Object was null inside handleCustomerSubscriptionUpdated");
            return;
        }

        SubscriptionStatus status = mapStripeStatusToEnum(subscription.getStatus());
        if (status == null) {
            log.warn("Unknown status {} for subscription {}", subscription.getStatus(), subscription.getId());
            return;
        }

        SubscriptionItem item = subscription.getItems().getData().getFirst();
        Instant currentPeriodStart = Instant.ofEpochSecond(item.getCurrentPeriodStart());
        Instant currentPeriodEnd = Instant.ofEpochSecond(item.getCurrentPeriodEnd());

        Long planId = resolvePlanId(item.getPrice());

        subscriptionService.updateSubscription(subscription.getId(), status, currentPeriodStart, currentPeriodEnd,
                subscription.getCancelAtPeriodEnd(), planId);
    }

    private void handleCustomerSubscriptionDeleted(Subscription subscription) {
        if (subscription == null) {
            log.error("Subscription Object was null inside handleCustomerSubscriptionDeleted.");
            return;
        }

        subscriptionService.cancelSubscription(subscription.getId());
    }

    private void handleInvoicePaid(Invoice invoice) {
        String subId = extractSubscriptionId(invoice);
        if (subId == null)
            return;

        try {
            Subscription subscription = Subscription.retrieve(subId);
            SubscriptionItem items = subscription.getItems().getData().getFirst();
            Instant periodStart = Instant.ofEpochSecond(items.getCurrentPeriodStart());
            Instant periodEnd = Instant.ofEpochSecond(items.getCurrentPeriodEnd());

            subscriptionService.renewSubscriptionPeriod(
                    subId, periodStart, periodEnd);

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleInvoicePaymentFailed(Invoice invoice) {
        String subId = extractSubscriptionId(invoice);
        if (subId == null)
            return;

        subscriptionService.markSubscriptionPastDue(subId);

    }

    /// Utilities Methods

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user", userId.toString()));
    }

    private SubscriptionStatus mapStripeStatusToEnum(String status) {

        return switch (status) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trailing" -> SubscriptionStatus.TRAILING;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            default -> {
                log.warn("Unmapped Session Status {}", status);
                yield null;
            }
        };
    }

    private Long resolvePlanId(Price price) {

        if (price == null || price.getId() == null)
            return null;

        return planRepository.findByStripePriceId(price.getId())
                .map(Plan::getId)
                .orElse(null);
    }

    private String extractSubscriptionId(Invoice invoice) {
        var parent = invoice.getParent();
        if (parent == null)
            return null;

        var subDetails = parent.getSubscriptionDetails();
        if (subDetails == null)
            return null;

        return subDetails.getSubscription();
    }
}
