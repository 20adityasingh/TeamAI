package com.distributed.teamai.common_lib.dto;

public record UserDto(
        Long id,
        String name,
        String username,
        String password
) {
}
