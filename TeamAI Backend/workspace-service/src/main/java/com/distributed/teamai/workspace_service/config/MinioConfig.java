package com.distributed.teamai.workspace_service.config;


import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfig {

    private String url;
    private String accessKey;
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        String envAccessKey = System.getenv("MINIO_ROOT_USER");
        String envSecretKey = System.getenv("MINIO_ROOT_PASSWORD");

        String effectiveUrl = (url != null && !url.isEmpty()) ? url : "http://minio:9000";
        String effectiveAccessKey = (envAccessKey != null && !envAccessKey.isEmpty()) ? envAccessKey : accessKey;
        String effectiveSecretKey = (envSecretKey != null && !envSecretKey.isEmpty()) ? envSecretKey : secretKey;

        System.out.println("DEBUG: Initializing MinioClient with endpoint: " + effectiveUrl + ", AccessKey length: " + (effectiveAccessKey != null ? effectiveAccessKey.length() : "NULL"));

        return MinioClient.builder()
                .endpoint(effectiveUrl)
                .credentials(effectiveAccessKey, effectiveSecretKey)
                .build();
    }
}
