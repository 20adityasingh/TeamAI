package com.distributed.teamai.account_service.mapper;

import com.distributed.teamai.account_service.dto.subscription.SubscriptionResponse;
import com.distributed.teamai.account_service.entity.Plan;
import com.distributed.teamai.account_service.entity.Subscription;
import com.distributed.teamai.common_lib.dto.PlanDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @org.mapstruct.Mapping(target = "tokenUsedThisCycle", ignore = true)
    SubscriptionResponse toSubscriptionResponse(Subscription subscription);

    @org.mapstruct.Mapping(target = "Price", ignore = true)
    PlanDto toPlanResponse(Plan plan);

}
