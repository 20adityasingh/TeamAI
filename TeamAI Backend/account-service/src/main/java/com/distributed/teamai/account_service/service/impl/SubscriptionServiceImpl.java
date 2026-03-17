package com.distributed.teamai.account_service.service.impl;

import com.distributed.teamai.account_service.dto.subscription.SubscriptionResponse;
import com.distributed.teamai.account_service.entity.Plan;
import com.distributed.teamai.account_service.entity.Subscription;
import com.distributed.teamai.account_service.entity.User;
import com.distributed.teamai.common_lib.dto.PlanDto;
import com.distributed.teamai.common_lib.enums.SubscriptionStatus;
import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import com.distributed.teamai.account_service.mapper.SubscriptionMapper;
import com.distributed.teamai.account_service.repository.PlanRepository;
import com.distributed.teamai.account_service.repository.SubscriptionRepository;
import com.distributed.teamai.account_service.repository.UserRepository;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.account_service.service.SubscriptionService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;


@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    AuthUtils authUtils;
    SubscriptionRepository subscriptionRepository;
    SubscriptionMapper subscriptionMapper;
    UserRepository userRepository;
    PlanRepository planRepository;

    Integer MAX_PROJECT_ALLOWED_AT_FREE_TIER = 1;

    @Override
    public SubscriptionResponse getCurrentSubscription() {
        Long userId = authUtils.getCurrentUserId();

        var subscriptionOpt = subscriptionRepository.findByUserIdAndStatusIn(userId, Set.of(
                SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.TRAILING
        ));

        if (subscriptionOpt.isPresent()) {
            Subscription sub = subscriptionOpt.get();
            SubscriptionResponse response = subscriptionMapper.toSubscriptionResponse(sub);
            PlanDto planWithPrice = populatePrice(response.plan(), sub.getPlan());
            return new SubscriptionResponse(
                    planWithPrice,
                    response.status(),
                    response.currentPeriodEnd(),
                    response.tokenUsedThisCycle()
            );
        }

        // Return Free Plan details as default
        Plan freePlan = planRepository.findByNameIgnoreCase("FREE PLAN")
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "FREE PLAN"));

        PlanDto freePlanDto = subscriptionMapper.toPlanResponse(freePlan);
        freePlanDto = populatePrice(freePlanDto, freePlan);

        return new SubscriptionResponse(
                freePlanDto,
                SubscriptionStatus.FREE.name(),
                null,
                0L
        );
    }

    private PlanDto populatePrice(PlanDto dto, Plan entity) {
        String priceString = "Contact Support";
        try {
            if (entity.getStripePriceId() != null && !entity.getStripePriceId().isEmpty() && !entity.getStripePriceId().equals("no")) {
                com.stripe.model.Price stripePrice = com.stripe.model.Price.retrieve(entity.getStripePriceId());
                if (stripePrice.getUnitAmount() != null) {
                    double amount = stripePrice.getUnitAmount() / 100.0;
                    String symbol = "$";
                    if ("inr".equalsIgnoreCase(stripePrice.getCurrency())) {
                        symbol = "₹";
                    } else if ("eur".equalsIgnoreCase(stripePrice.getCurrency())) {
                        symbol = "€";
                    }
                    priceString = String.format("%s%.2f", symbol, amount);
                    if (priceString.endsWith(".00")) {
                        priceString = priceString.substring(0, priceString.length() - 3);
                    }
                }
            } else if ("no".equals(entity.getStripePriceId())) {
                priceString = "Free";
            }
        } catch (Exception e) {
            log.error("Failed to fetch price for plan {}: {}", entity.getName(), e.getMessage());
        }

        return new PlanDto(
                dto.id(),
                dto.name(),
                dto.maxProjects(),
                dto.maxTokensPerDay(),
                dto.maxPreviews(),
                dto.unlimitedAi(),
                priceString
        );
    }

    @Override
    public void activateSubscription(Long userId, Long planId, String subscriptionId, String customerId) {
        boolean exists = subscriptionRepository.existsByStripeSubscriptionId(subscriptionId);
        if(exists) return;

        User user = getUser(userId);

        Plan plan = getPlan(planId);

        Subscription subscription = Subscription.builder()
                .plan(plan)
                .user(user)
                .cancelAtPeriodEnd(false)
                .status(SubscriptionStatus.INCOMPLETE)
                .stripeSubscriptionId(subscriptionId)
                .build();

        subscriptionRepository.save(subscription);

    }

    @Override
    public void updateSubscription(String subscriptionId, SubscriptionStatus status, Instant currentPeriodStart, Instant currentPeriodEnd, Boolean cancelAtPeriodEnd, Long planId) {
        Subscription subscription = getSubscription(subscriptionId);

        boolean hasSubscriptionUpdated = false;

        if(status != null && status != subscription.getStatus()){
            subscription.setStatus(status);
            hasSubscriptionUpdated = true;
        }

        if(currentPeriodStart != null && !currentPeriodStart.equals(subscription.getCurrentPeriodStart())){
            subscription.setCurrentPeriodStart(currentPeriodStart);
            hasSubscriptionUpdated = true;
        }

        if(currentPeriodEnd != null && !currentPeriodEnd.equals(subscription.getCurrentPeriodEnd())){
            subscription.setCurrentPeriodEnd(currentPeriodEnd);
            hasSubscriptionUpdated = true;
        }

        if(cancelAtPeriodEnd != null && cancelAtPeriodEnd != subscription.getCancelAtPeriodEnd()){
            subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
            hasSubscriptionUpdated = true;
        }

        if(planId != null && !planId.equals(subscription.getPlan().getId())){
            Plan plan = getPlan(planId);
            subscription.setPlan(plan);
            hasSubscriptionUpdated = true;
        }

        if (hasSubscriptionUpdated) {
            log.debug("Subscription has been updated: {}", subscriptionId);
            subscriptionRepository.save(subscription);
        }

    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        Subscription subscription = getSubscription(subscriptionId);

        subscription.setStatus(SubscriptionStatus.CANCELED);

        subscriptionRepository.save(subscription);
    }

    @Override
    public void renewSubscriptionPeriod(String subId, Instant periodStart, Instant periodEnd) {
        Subscription subscription = getSubscription(subId);

        Instant newStart = periodStart != null ? periodStart : subscription.getCurrentPeriodEnd();

        subscription.setCurrentPeriodStart(newStart);
        subscription.setCurrentPeriodEnd(periodEnd);

        if(subscription.getStatus() == SubscriptionStatus.PAST_DUE || subscription.getStatus() == SubscriptionStatus.INCOMPLETE){
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }

        subscriptionRepository.save(subscription);

    }

    @Override
    public void markSubscriptionPastDue(String subId) {

        Subscription subscription = getSubscription(subId);

        if(subscription.getStatus() == SubscriptionStatus.PAST_DUE){
            log.debug("Subscription is already PAST DUE, subscription ID: {}", subId);
            return;
        }

        subscription.setStatus(SubscriptionStatus.PAST_DUE);

        subscriptionRepository.save(subscription);

    }

    @Override
    public PlanDto getCurrentSubscribedPlanByUser() {

        SubscriptionResponse subscriptionResponse = getCurrentSubscription();

        return subscriptionResponse.plan();
    }

    /// Utilities Methods

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("user", userId.toString())
                );
    }

    private Plan getPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("user", planId.toString())
                );
    }

    private Subscription getSubscription(String subId) {
        return subscriptionRepository.findByStripeSubscriptionId(subId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("subscription", subId)
                );
    }
}
