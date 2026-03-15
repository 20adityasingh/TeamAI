package com.distributed.teamai.workspace_service.service;


import com.distributed.teamai.workspace_service.dto.deploy.DeployResponse;

public interface DeploymentService {

    DeployResponse deploy(Long projectId);
}
