package com.distributed.teamai.account_service.dto.auth;

public record AuthResponse(String token, UserProfileResponse user) {
}
