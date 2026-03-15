package com.distributed.teamai.account_service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @Email
        @NotBlank
        String username,

        @NotBlank
        @Size(min = 2, max = 13)
        String name,

        @NotBlank
        @Size(min = 6, max = 12)
        String password
) {
}
