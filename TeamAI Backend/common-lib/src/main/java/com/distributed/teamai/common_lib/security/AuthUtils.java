package com.distributed.teamai.common_lib.security;


import com.distributed.teamai.common_lib.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

@Component
public class AuthUtils {

    @Value("${jwt.secret-key}")
    private String secret_key;

    public SecretKey getSecretKey(){
        return Keys.hmacShaKeyFor(secret_key.getBytes(StandardCharsets.UTF_8));
    }

    public String getAccessToken(UserDto user){
        return Jwts.builder()
                .subject(user.username())
                .claim("userId", user.id().toString())
                .claim("name", user.name())
                .signWith(getSecretKey())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000*60*60))
                .compact();
    }

    public JwtUserPrincipal verifyAccessToken (String token){
        Claims claims =  Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.parseLong(claims.get("userId", String.class));
        String username = claims.getSubject();
        String name = claims.get("name", String.class);

        return new JwtUserPrincipal(userId, name , username, null,new ArrayList<>());
    }

    public Long getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal user)){
            throw new AuthenticationCredentialsNotFoundException("You are not Authenticated");
        }

        return user.userId();

    }
}
