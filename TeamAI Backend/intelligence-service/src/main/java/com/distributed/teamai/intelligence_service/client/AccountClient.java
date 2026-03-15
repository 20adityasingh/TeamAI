package com.distributed.teamai.intelligence_service.client;

import com.distributed.teamai.common_lib.dto.PlanDto;
import com.distributed.teamai.common_lib.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@FeignClient(name = "account-service", path = "/account", url = "${ACCOUNT_SERVICE_URI:}")
public interface AccountClient {

    @GetMapping("/internal/v1/users/{userId}")
    UserDto getUserById(@PathVariable Long userId);

    @GetMapping("/internal/v1/users/by-username")
    Optional<UserDto> getUserByUsername(@RequestParam("username") String username);

    @GetMapping("/internal/v1/billing/current-plan")
    PlanDto getCurrentSubscriptionPlan();

}
