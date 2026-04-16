package com.student.service.dataBase;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO文件存储服务
 * 提供文件上传、下载、删除等操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${imkqas.storage.minio.bucket:medical-documents}")
    private String bucketName;

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @param objectName 存储的对象名称（包含路径）
     * @return 文件访问URL
     */
    public String uploadFile(MultipartFile file, String objectName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        try (InputStream inputStream = file.getInputStream()) {
            // 获取文件信息
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            long fileSize = file.getSize();

            log.info("上传文件: originalFilename={}, objectName={}, size={} bytes, contentType={}",
                    originalFilename, objectName, fileSize, contentType);

            // 上传文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, fileSize, -1)
                    .contentType(contentType)
                    .build());

            log.info("文件上传成功: bucket={}, object={}", bucketName, objectName);

            // 生成文件访问URL
            String fileUrl = getFileUrl(objectName);
            log.debug("文件访问URL: {}", fileUrl);

            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败: objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 上传文件（自动生成对象名称）
     *
     * @param file 上传的文件
     * @param prefix 存储前缀（如 "documents/"）
     * @return 文件访问URL
     */
    public String uploadFile(MultipartFile file, String prefix, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 生成对象名称: prefix/userId/timestamp/originalFilename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String objectName = String.format("%s/%d/%s/%s%s",
                prefix.trim().replaceAll("^/|/$", ""),
                userId,
                timestamp,
                originalFilename != null ? originalFilename.replaceAll("\\.[^.]+$", "") : "file",
                extension);

        return uploadFile(file, objectName);
    }

    /**
     * 获取文件访问URL（带有效期）
     *
     * @param objectName 对象名称
     * @param expiry 有效期（秒），默认7天
     * @return 文件访问URL
     */
    public String getFileUrl(String objectName, int expiry) {
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expiry, TimeUnit.SECONDS)
                    .build());

            log.debug("生成文件访问URL: objectName={}, expiry={}s, url={}", objectName, expiry, url);
            return url;
        } catch (Exception e) {
            log.error("生成文件访问URL失败: objectName={}", objectName, e);
            throw new RuntimeException("生成文件访问URL失败", e);
        }
    }

    /**
     * 获取文件访问URL（默认7天有效期）
     *
     * @param objectName 对象名称
     * @return 文件访问URL
     */
    public String getFileUrl(String objectName) {
        return getFileUrl(objectName, 7 * 24 * 60 * 60); // 7天
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    public InputStream downloadFile(String objectName) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());

            log.info("文件下载成功: objectName={}", objectName);
            return response;
        } catch (Exception e) {
            log.error("文件下载失败: objectName={}", objectName, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     * @return 是否删除成功
     */
    public boolean deleteFile(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());

            log.info("文件删除成功: objectName={}", objectName);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败: objectName={}", objectName, e);
            return false;
        }
    }

    /**
     * 批量删除文件
     *
     * @param objectNames 对象名称列表
     * @return 删除失败的对象列表
     */
    public List<String> deleteFiles(List<String> objectNames) {
        List<String> failedObjects = new ArrayList<>();

        if (objectNames == null || objectNames.isEmpty()) {
            return failedObjects;
        }

        try {
            // 构建删除对象列表
            List<DeleteObject> objects = objectNames.stream()
                    .map(DeleteObject::new)
                    .toList();

            // 执行批量删除
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucketName)
                    .objects(objects)
                    .build());

            // 检查删除结果
            for (Result<DeleteError> result : results) {
                try {
                    DeleteError error = result.get();
                    failedObjects.add(error.objectName());
                    log.error("文件删除失败: objectName={}, error={}", error.objectName(), error.message());
                } catch (Exception e) {
                    // 忽略，表示删除成功
                }
            }

            log.info("批量删除文件完成: total={}, failed={}", objectNames.size(), failedObjects.size());
            return failedObjects;
        } catch (Exception e) {
            log.error("批量删除文件失败", e);
            throw new RuntimeException("批量删除文件失败", e);
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称
     * @return 是否存在
     */
    public boolean fileExists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());

            log.debug("文件存在: objectName={}", objectName);
            return true;
        } catch (Exception e) {
            log.debug("文件不存在: objectName={}", objectName);
            return false;
        }
    }

    /**
     * 获取文件信息
     *
     * @param objectName 对象名称
     * @return 文件信息
     */
    public StatObjectResponse getFileInfo(String objectName) {
        try {
            StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());

            log.debug("获取文件信息成功: objectName={}, size={}, contentType={}",
                    objectName, response.size(), response.contentType());
            return response;
        } catch (Exception e) {
            log.error("获取文件信息失败: objectName={}", objectName, e);
            throw new RuntimeException("获取文件信息失败", e);
        }
    }

    /**
     * 列出存储桶中的文件
     *
     * @param prefix 前缀（如 "documents/"）
     * @param recursive 是否递归列出
     * @return 文件列表
     */
    public List<String> listFiles(String prefix, boolean recursive) {
        List<String> files = new ArrayList<>();

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .recursive(recursive)
                    .build());

            for (Result<Item> result : results) {
                try {
                    Item item = result.get();
                    if (!item.isDir()) {
                        files.add(item.objectName());
                    }
                } catch (Exception e) {
                    log.warn("获取文件列表项失败", e);
                }
            }

            log.debug("列出文件: prefix={}, recursive={}, count={}", prefix, recursive, files.size());
            return files;
        } catch (Exception e) {
            log.error("列出文件失败: prefix={}", prefix, e);
            throw new RuntimeException("列出文件失败", e);
        }
    }

    /**
     * 复制文件
     *
     * @param sourceObjectName 源对象名称
     * @param targetObjectName 目标对象名称
     * @return 是否复制成功
     */
    public boolean copyFile(String sourceObjectName, String targetObjectName) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(targetObjectName)
                    .source(CopySource.builder()
                            .bucket(bucketName)
                            .object(sourceObjectName)
                            .build())
                    .build());

            log.info("文件复制成功: source={}, target={}", sourceObjectName, targetObjectName);
            return true;
        } catch (Exception e) {
            log.error("文件复制失败: source={}, target={}", sourceObjectName, targetObjectName, e);
            return false;
        }
    }

    /**
     * 获取存储桶使用情况
     *
     * @return 存储桶使用情况信息
     */
    public BucketUsageInfo getBucketUsage() {
        try {
            // 列出所有文件并计算总大小
            List<String> files = listFiles("", true);
            long totalSize = 0;
            int fileCount = 0;

            for (String objectName : files) {
                try {
                    StatObjectResponse stat = getFileInfo(objectName);
                    totalSize += stat.size();
                    fileCount++;
                } catch (Exception e) {
                    log.warn("获取文件大小失败: {}", objectName, e);
                }
            }

            BucketUsageInfo usageInfo = new BucketUsageInfo();
            usageInfo.setBucketName(bucketName);
            usageInfo.setFileCount(fileCount);
            usageInfo.setTotalSize(totalSize);
            usageInfo.setTotalSizeFormatted(formatFileSize(totalSize));

            log.debug("获取存储桶使用情况: bucket={}, fileCount={}, totalSize={}",
                    bucketName, fileCount, usageInfo.getTotalSizeFormatted());

            return usageInfo;
        } catch (Exception e) {
            log.error("获取存储桶使用情况失败", e);
            throw new RuntimeException("获取存储桶使用情况失败", e);
        }
    }

    /**
     * 格式化文件大小
     *
     * @param size 文件大小（字节）
     * @return 格式化后的字符串
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 存储桶使用情况信息类
     */
    public static class BucketUsageInfo {
        private String bucketName;
        private int fileCount;
        private long totalSize;
        private String totalSizeFormatted;

        // Getters and Setters
        public String getBucketName() { return bucketName; }
        public void setBucketName(String bucketName) { this.bucketName = bucketName; }
        public int getFileCount() { return fileCount; }
        public void setFileCount(int fileCount) { this.fileCount = fileCount; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
        public String getTotalSizeFormatted() { return totalSizeFormatted; }
        public void setTotalSizeFormatted(String totalSizeFormatted) { this.totalSizeFormatted = totalSizeFormatted; }
    }
}