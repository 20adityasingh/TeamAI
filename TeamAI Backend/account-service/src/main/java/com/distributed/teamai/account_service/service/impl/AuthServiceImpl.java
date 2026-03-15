package com.distributed.teamai.account_service.service.impl;

import com.distributed.teamai.account_service.dto.auth.AuthResponse;
import com.distributed.teamai.account_service.dto.auth.LoginRequest;
import com.distributed.teamai.account_service.dto.auth.SignupRequest;
import com.distributed.teamai.account_service.dto.auth.SignupResponse;
import com.distributed.teamai.account_service.entity.User;
import com.distributed.teamai.common_lib.dto.UserDto;
import com.distributed.teamai.common_lib.error.BadRequestException;
import com.distributed.teamai.account_service.mapper.UserMapper;
import com.distributed.teamai.account_service.repository.UserRepository;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.account_service.service.AuthService;
import com.distributed.teamai.common_lib.security.JwtUserPrincipal;
import io.jsonwebtoken.Jwt;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    AuthUtils authUtils;
    AuthenticationManager authenticationManager;

    @Override
    public SignupResponse signup(SignupRequest request) {

        userRepository.findByUsernameIgnoreCase(request.username()).ifPresent(
                user -> {
                    throw new BadRequestException("User with username " + request.username() + " already exist.");
                });

        User user = userMapper.toEntityUser(request);

        user.setPassword(passwordEncoder.encode(request.password()));

        user = userRepository.save(user);

        JwtUserPrincipal principal = new JwtUserPrincipal(user.getId(), user.getName() , user.getUsername() , null, new ArrayList<>());

        return new SignupResponse(userMapper.toUserProfileResponse(principal));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // User user = userRepository.findByUsername(request.username()).orElseThrow(()
        // -> new BadRequestException("Username or Password is incorrect."));
        //
        // if(!passwordEncoder.matches(request.password(), user.getPassword())){
        // throw new BadRequestException("Username or Password is incorrect.");
        // }
        //
        // return new AuthResponse( authUtils.getAccessToken(user),
        // userMapper.toUserProfileResponse(user));

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        JwtUserPrincipal user = (JwtUserPrincipal) authentication.getPrincipal();

        return new AuthResponse(authUtils.getAccessToken(userMapper.toUserDto(user)), userMapper.toUserProfileResponse(user));

    }
}
