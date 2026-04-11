package com.student.config;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO配置类
 * 配置MinIO客户端连接
 *
 * @author 系统
 * @version 1.0
 */
@Configuration  // 暂时禁用MinIO配置
@Slf4j
public class MinioConfig {

    @Value("${imkqas.storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${imkqas.storage.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${imkqas.storage.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${imkqas.storage.minio.bucket:medical-documents}")
    private String bucketName;

    /**
     * 创建MinIO客户端
     *
     * @return MinioClient实例
     */
    @Bean
    public MinioClient minioClient() {
        try {
            log.info("初始化MinIO客户端: endpoint={}, bucket={}", endpoint, bucketName);

            MinioClient minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // 测试连接
            boolean isConnected = testConnection(minioClient);
            if (!isConnected) {
                log.warn("MinIO连接测试失败，但客户端已创建");
            } else {
                log.info("MinIO连接测试成功");
            }

            // 确保存储桶存在
            ensureBucketExists(minioClient);

            return minioClient;
        } catch (Exception e) {
            log.error("MinIO客户端初始化失败", e);
            throw new RuntimeException("MinIO客户端初始化失败", e);
        }
    }

    /**
     * 测试MinIO连接
     *
     * @param minioClient MinIO客户端
     * @return 连接是否成功
     */
    private boolean testConnection(MinioClient minioClient) {
        try {
            minioClient.listBuckets();
            return true;
        } catch (Exception e) {
            log.warn("MinIO连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 确保存储桶存在，不存在则创建
     *
     * @param minioClient MinIO客户端
     */
    private void ensureBucketExists(MinioClient minioClient) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());

            if (!exists) {
                log.info("创建MinIO存储桶: {}", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

                // 设置存储桶策略（公开读）
                String policy = String.format("""
                    {
                        "Version": "2012-10-17",
                        "Statement": [
                            {
                                "Effect": "Allow",
                                "Principal": {"AWS": ["*"]},
                                "Action": ["s3:GetObject"],
                                "Resource": ["arn:aws:s3:::%s/*"]
                            }
                        ]
                    }
                    """, bucketName);

                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());

                log.info("存储桶 {} 创建成功并设置公开读策略", bucketName);
            } else {
                log.debug("存储桶 {} 已存在", bucketName);
            }
        } catch (Exception e) {
            log.warn("存储桶检查/创建失败: {}", e.getMessage());
        }
    }

    // 存储桶名称通过 @Value 注入，无需定义为 @Bean
}