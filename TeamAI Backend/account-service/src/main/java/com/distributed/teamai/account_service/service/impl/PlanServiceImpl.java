package com.distributed.teamai.account_service.service.impl;

import com.distributed.teamai.account_service.entity.Plan;
import com.distributed.teamai.account_service.repository.PlanRepository;
import com.distributed.teamai.account_service.service.PlanService;
import com.distributed.teamai.common_lib.dto.PlanDto;
import com.stripe.model.Price;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class PlanServiceImpl implements PlanService {

    PlanRepository planRepository;

    @Override
    public List<PlanDto> getAllActivePlans() {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getActive() != null && plan.getActive())
                .map(this::toPlanDto)
                .collect(Collectors.toList());
    }

    private PlanDto toPlanDto(Plan plan) {
        String priceString = "Contact Support";
        try {
            if (plan.getStripePriceId() != null && !plan.getStripePriceId().isEmpty() && !plan.getStripePriceId().equals("no")) {
                Price stripePrice = Price.retrieve(plan.getStripePriceId());
                if (stripePrice.getUnitAmount() != null) {
                    double amount = stripePrice.getUnitAmount() / 100.0;
                    String symbol = "$";
                    if ("inr".equalsIgnoreCase(stripePrice.getCurrency())) symbol = "₹";
                    else if ("eur".equalsIgnoreCase(stripePrice.getCurrency())) symbol = "€";

                    priceString = String.format("%s%.2f", symbol, amount);
                    if (priceString.endsWith(".00")) {
                        priceString = priceString.substring(0, priceString.length() - 3);
                    }
                }
            } else if ("no".equals(plan.getStripePriceId())) {
                priceString = "Free";
            }
        } catch (Exception e) {
            log.error("Failed to fetch price for plan {}: {}", plan.getName(), e.getMessage());
        }

        return new PlanDto(plan.getId(), plan.getName(), plan.getMaxProjects(),
                plan.getMaxTokensPerDay(), plan.getMaxPreviews(), plan.getUnlimitedAi(), priceString);
    }
}
