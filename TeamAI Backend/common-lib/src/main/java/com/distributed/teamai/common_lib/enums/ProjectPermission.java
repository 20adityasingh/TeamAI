package com.distributed.teamai.common_lib.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ProjectPermission {

    VIEW("project:view"),
    EDIT("project:edit"),
    DELETE("project:delete"),

    MANAGE_MEMBER("project_member:manage"),
    VIEW_MEMBER("project_member:view");

    private final String value;
}
