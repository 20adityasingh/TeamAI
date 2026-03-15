package com.distributed.teamai.workspace_service.mapper;

import com.distributed.teamai.workspace_service.dto.member.MemberResponse;
import com.distributed.teamai.workspace_service.entity.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(target = "userId", source = "id.userId")
    @Mapping(target = "role", source = "projectRole")
    MemberResponse toMemberResponseFromMember(ProjectMember member);

//    @Mapping(target = "userId", source = "id")
//    @Mapping(target = "role", constant = "OWNER")
//    MemberResponse toMemberResponseFromOwner(User owner);
}
