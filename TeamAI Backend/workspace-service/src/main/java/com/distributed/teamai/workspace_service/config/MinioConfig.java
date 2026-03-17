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
        String effectiveUrl = (url != null && !url.isEmpty()) ? url : "http://minio:9000";
        String effectiveAccessKey = (accessKey != null && !accessKey.isEmpty()) ? accessKey : System.getenv("MINIO_ROOT_USER");
        String effectiveSecretKey = (secretKey != null && !secretKey.isEmpty()) ? secretKey : System.getenv("MINIO_ROOT_PASSWORD");

        return MinioClient.builder()
                .endpoint(effectiveUrl)
                .credentials(effectiveAccessKey, effectiveSecretKey)
                .build();
    }
}
