package com.distributed.teamai.account_service.service;


import com.distributed.teamai.account_service.dto.auth.AuthResponse;
import com.distributed.teamai.account_service.dto.auth.LoginRequest;
import com.distributed.teamai.account_service.dto.auth.SignupRequest;
import com.distributed.teamai.account_service.dto.auth.SignupResponse;

public interface AuthService {
    SignupResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);
}
