package com.distributed.teamai.workspace_service.service.impl;

import com.distributed.teamai.common_lib.dto.PlanDto;
import com.distributed.teamai.common_lib.error.BadRequestException;
import com.distributed.teamai.common_lib.security.AuthUtils;
import com.distributed.teamai.workspace_service.client.AccountClient;
import com.distributed.teamai.workspace_service.dto.deploy.DeployResponse;
import com.distributed.teamai.workspace_service.repository.ProjectMemberRepository;
import com.distributed.teamai.workspace_service.service.DeploymentService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesDeploymentServiceImpl implements DeploymentService {

    private final KubernetesClient client;
    private final StringRedisTemplate redisTemplate;
    private final AuthUtils authUtils;
    private final AccountClient accountClient;
    private final ProjectMemberRepository projectMemberRepository;

    @Value("${app.preview.namespace}")
    private String namespace;

    @Value("${app.preview.domain}")
    private String baseDomain;

    @Value("${app.preview.proxy-port}")
    private String proxyPort;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private static final String POOL_LABEL = "status";
    private static final String PROJECT_LABEL = "project-id";
    private static final String IDLE = "idle";
    private static final String BUSY = "busy";

    public DeployResponse deploy(Long projectId) {
        if (!canDeployPreview()) {
            throw new BadRequestException("Preview limit reached for current PLAN, upgrade your PLAN.");
        }

        // Dynamically build the domain: project-123.app.domain.com
        String domain = "project-" + projectId + "." + baseDomain;

        // Use default port 80 format logic for clean URLs, or explicit ports for local testing
        String formattedUrl = proxyPort.equals("80")
                ? "http://" + domain
                : "http://" + domain + ":" + proxyPort;

        Pod existingPod = findActivePod(projectId);

        if (existingPod != null) {
            String podName = existingPod.getMetadata().getName();
            log.info("Found existing pod {} for project {}. Resuming...", podName, projectId);
            
            try {
                // Safely install dependencies by restarting the dev server to prevent Vite ENOENT crashes
                String restartCmd = "killall node 2>/dev/null || true; npm install && nohup npm run dev -- --host 0.0.0.0 --port 5173 > /app/dev.log 2>&1 &";
                execCommand(podName, "runner", "sh", "-c", restartCmd);
            } catch (Exception e) {
                log.warn("Failed to restart dev server on pod {}: {}", podName, e.getMessage());
            }

            registerRoute(domain, existingPod);
            return new DeployResponse(formattedUrl);
        }

        return claimAndStartNewPod(projectId, domain, formattedUrl);
    }

    private boolean canDeployPreview() {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            throw new BadRequestException("User not found.");
        }

        PlanDto plan = accountClient.getCurrentSubscriptionPlan();
        int maxPreviews = (plan != null && plan.maxPreviews() != null) ? plan.maxPreviews() : 1;
        List<Long> ownedProjectIds = projectMemberRepository.findAllOwnedProjectIdsByUser(userId);

        if (ownedProjectIds.isEmpty())
            return true;

        long activePreviewsCount = client.pods().inNamespace(namespace)
                .withLabel(POOL_LABEL, BUSY)
                .list().getItems().stream()
                .filter(pod -> {
                    String podProjectId = pod.getMetadata().getLabels().get(PROJECT_LABEL);
                    return podProjectId != null && ownedProjectIds.contains(Long.parseLong(podProjectId));
                })
                .count();

        return activePreviewsCount < maxPreviews;
    }

    private Pod findActivePod(Long projectId) {
        return client.pods().inNamespace(namespace)
                .withLabel(PROJECT_LABEL, projectId.toString())
                .withLabel(POOL_LABEL, BUSY)
                .list().getItems().stream()
                .filter(pod -> pod.getStatus().getPhase().equals("Running"))
                .findFirst()
                .orElse(null);
    }

    private DeployResponse claimAndStartNewPod(Long projectId, String domain, String formattedUrl) {
        Pod pod = client.pods().inNamespace(namespace)
                .withLabel(POOL_LABEL, IDLE)
                .list().getItems().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No idle runners available. Please scale up the runner-pool."));

        String podName = pod.getMetadata().getName();
        log.info("Claiming pod {} for project {}", podName, projectId);

        client.pods().inNamespace(namespace).withName(podName).edit(p -> {
            p.getMetadata().getLabels().put(POOL_LABEL, BUSY);
            p.getMetadata().getLabels().put(PROJECT_LABEL, projectId.toString());
            return p;
        });

        try {
            String initialSyncCmd = String.format("rm -rf /app/* && mc mirror --overwrite myminio/%s/%d/ /app/", bucketName, projectId);
            execCommand(podName, "syncer", "sh", "-c", initialSyncCmd);

            String watchCmd = String.format("nohup mc mirror --overwrite --watch myminio/%s/%d/ /app/ > /app/sync.log 2>&1 &", bucketName, projectId);
            execCommand(podName, "syncer", "sh", "-c", watchCmd);

            String startCmd = "npm install && nohup npm run dev -- --host 0.0.0.0 --port 5173 > /app/dev.log 2>&1 &";
            execCommand(podName, "runner", "sh", "-c", startCmd);

            Pod updatedPod = client.pods().inNamespace(namespace).withName(podName).get();
            registerRoute(domain, updatedPod);

            log.info("Deployment successful: {}", formattedUrl);
            return new DeployResponse(formattedUrl);

        } catch (Exception e) {
            log.error("Deployment failed for project {}. Releasing pod {}.", projectId, podName, e);
            client.pods().inNamespace(namespace).withName(podName).delete();
            throw new RuntimeException("Failed to deploy project " + projectId + ": " + e.getMessage(), e);
        }
    }

    private void registerRoute(String domain, Pod pod) {
        String podIp = pod.getStatus().getPodIP();
        if (podIp == null) throw new RuntimeException("Pod is running but has no IP!");

        redisTemplate.opsForValue().set("route:" + domain, podIp + ":5173", 6, TimeUnit.HOURS);
        log.info("Route Registered: {} -> {}", domain, podIp);
    }

    private void execCommand(String podName, String container, String... command) {
        log.debug("Exec in {}:{} -> {}", podName, container, String.join(" ", command));

        CompletableFuture<String> data = new CompletableFuture<>();
        try (ExecWatch ignored = client.pods().inNamespace(namespace).withName(podName)
                .inContainer(container)
                .writingOutput(new ByteArrayOutputStream())
                .writingError(new ByteArrayOutputStream())
                .usingListener(new ExecListener() {
                    @Override
                    public void onClose(int code, String reason) {
                        data.complete("Done");
                    }
                })
                .exec(command)) {

            if (command[command.length - 1].trim().endsWith("&")) {
                Thread.sleep(500);
            } else {
                data.get(30, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            log.error("Exec failed", e);
            throw new RuntimeException("Pod Execution Failed", e);
        }
    }
}
