package com.distributed.teamai.common_lib.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

import static com.distributed.teamai.common_lib.enums.ProjectPermission.*;

@RequiredArgsConstructor
@Getter
public enum ProjectRole {
    EDITOR(Set.of(VIEW, EDIT, DELETE, VIEW_MEMBER)),
    VIEWER(Set.of(VIEW, VIEW_MEMBER)),
    OWNER(Set.of(VIEW, EDIT, DELETE, MANAGE_MEMBER, VIEW_MEMBER));

    private final Set<ProjectPermission> permissions;
}
