package com.distributed.teamai.intelligence_service.security;

import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.intelligence_service.client.WorkspaceClient;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Component;


import static com.distributed.teamai.common_lib.enums.ProjectPermission.*;


@Component("Security")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class SecurityExpression {

    AuthUtils authUtils;
    WorkspaceClient workspaceClient;

    public boolean canViewProject(Long projectId){
        try {
            return workspaceClient.hasPermission(projectId, VIEW);
        } catch (FeignException.Unauthorized e) {
            log.warn("Unauthorized access when checking view permission for projectId {}. This may indicate expired credentials.", projectId, e);
            throw new CredentialsExpiredException("User credentials have expired. Please log in again.");
        } catch (FeignException e) {
            log.error("Error occurred while checking view permission for projectId {}: {}", projectId, e.getMessage(), e);
            return false;
        }
    }

    public boolean canEditProject(Long projectId){
        try {
            return workspaceClient.hasPermission(projectId, EDIT);
        } catch (FeignException.Unauthorized e) {
            log.warn("Unauthorized access when checking edit permission for projectId {}. This may indicate expired credentials.", projectId, e);
            throw new CredentialsExpiredException("User credentials have expired. Please log in again.");
        }catch (FeignException e) {
            log.error("Error occurred while checking edit permission for projectId {}: {}", projectId, e.getMessage(), e);
            return false;
        }
    }

    public boolean canDeleteProject(Long projectId){
        try {
            return workspaceClient.hasPermission(projectId, DELETE);
        } catch (FeignException.Unauthorized e) {
            log.warn("Unauthorized access when checking delete permission for projectId {}. This may indicate expired credentials.", projectId, e);
            throw new CredentialsExpiredException("User credentials have expired. Please log in again.");
        } catch (FeignException e) {
            log.error("Error occurred while checking delete permission for projectId {}: {}", projectId, e.getMessage(), e);
            return false;
        }
    }

    public boolean canViewMembers(Long projectId){
        try {
            return workspaceClient.hasPermission(projectId, VIEW_MEMBER);
        } catch (FeignException.Unauthorized e) {
            log.warn("Unauthorized access when checking view member permission for projectId {}. This may indicate expired credentials.", projectId, e);
            throw new CredentialsExpiredException("User credentials have expired. Please log in again.");
        } catch (FeignException e) {
            log.error("Error occurred while checking view member permission for projectId {}: {}", projectId, e.getMessage(), e);
            return false;
        }
    }

    public boolean canManageMembers(Long projectId){
        try {
            return workspaceClient.hasPermission(projectId, MANAGE_MEMBER);
        } catch (FeignException.Unauthorized e) {
            log.warn("Unauthorized access when checking manage member permission for projectId {}. This may indicate expired credentials.", projectId, e);
            throw new CredentialsExpiredException("User credentials have expired. Please log in again.");
        } catch (FeignException e) {
            log.error("Error occurred while checking manage member permission for projectId {}: {}", projectId, e.getMessage(), e);
            return false;
        }
    }
}
