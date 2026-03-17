package com.distributed.teamai.account_service.controller;


import com.distributed.teamai.account_service.entity.User;
import com.distributed.teamai.account_service.mapper.UserMapper;
import com.distributed.teamai.account_service.repository.UserRepository;
import com.distributed.teamai.account_service.service.SubscriptionService;
import com.distributed.teamai.common_lib.dto.PlanDto;
import com.distributed.teamai.common_lib.dto.UserDto;
import com.distributed.teamai.common_lib.error.ResourceNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalAccountController {


    UserRepository userRepository;

    UserMapper userMapper;

    SubscriptionService subscriptionService;

    @GetMapping("/users/{userId}")
    public UserDto getUserById(@PathVariable Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("User", userId.toString()
                        ));
        return userMapper.toUserDto(user);
    }

    @GetMapping("/users/by-username")
    public Optional<UserDto> getUserByUsername(@RequestParam String username) {
        return userRepository
                .findByUsernameIgnoreCase(username)
                .map(userMapper::toUserDto);
    }

    @GetMapping("/billing/current-plan")
    public PlanDto getCurrentSubscriptionPlan(@RequestParam(required = false) Long userId) {
        if (userId != null) {
            return subscriptionService.getCurrentSubscribedPlanByUser(userId);
        }
        return subscriptionService.getCurrentSubscribedPlanByUser();
    }

}
