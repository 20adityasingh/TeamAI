package com.distributed.teamai.account_service.mapper;

import com.distributed.teamai.account_service.dto.auth.SignupRequest;
import com.distributed.teamai.account_service.dto.auth.UserProfileResponse;
import com.distributed.teamai.account_service.entity.User;
import com.distributed.teamai.common_lib.dto.UserDto;
import com.distributed.teamai.common_lib.security.JwtUserPrincipal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntityUser (SignupRequest request);

    @Mapping(target = "id", source = "userId")
    UserProfileResponse toUserProfileResponse (JwtUserPrincipal user);

    UserDto toUserDto (User user);

    UserDto toUserDto (JwtUserPrincipal user);

}
