package com.student.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 环境变量工具类
 * 支持从.env文件、系统环境变量和Java系统属性读取配置
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
public class EnvUtil {

    private static final String ENV_FILE_NAME = ".env";
    private static Map<String, String> envCache = null;
    private static long lastModifiedTime = 0;

    /**
     * 获取环境变量值（优先级：.env文件 > 系统环境变量 > Java系统属性）
     *
     * @param key 环境变量键名
     * @return 环境变量值，未找到返回null
     */
    public static String getEnv(String key) {
        return getEnv(key, null);
    }

    /**
     * 获取环境变量值（优先级：.env文件 > 系统环境变量 > Java系统属性）
     *
     * @param key          环境变量键名
     * @param defaultValue 默认值
     * @return 环境变量值，未找到返回默认值
     */
    public static String getEnv(String key, String defaultValue) {
        // 从.env文件读取
        String envValue = getFromEnvFile(key);
        if (envValue != null) {
            log.debug("从.env文件读取环境变量: {}={}", key, maskSensitiveValue(key, envValue));
            return envValue;
        }

        // 从系统环境变量读取
        envValue = System.getenv(key);
        if (envValue != null) {
            log.debug("从系统环境变量读取: {}={}", key, maskSensitiveValue(key, envValue));
            return envValue;
        }

        // 从Java系统属性读取
        envValue = System.getProperty(key);
        if (envValue != null) {
            log.debug("从系统属性读取: {}={}", key, maskSensitiveValue(key, envValue));
            return envValue;
        }

        log.debug("环境变量未找到: {}, 使用默认值: {}", key, defaultValue != null ? maskSensitiveValue(key, defaultValue) : "null");
        return defaultValue;
    }

    /**
     * 从.env文件读取指定键的值
     */
    private static String getFromEnvFile(String key) {
        loadEnvFileIfNeeded();
        return envCache != null ? envCache.get(key) : null;
    }

    /**
     * 按需加载.env文件
     */
    private static synchronized void loadEnvFileIfNeeded() {
        try {
            // 查找.env文件（从当前工作目录开始向上查找）
            File envFile = findEnvFile();
            if (envFile == null || !envFile.exists()) {
                log.debug(".env文件不存在，跳过加载");
                envCache = null;
                return;
            }

            // 检查文件是否已修改
            long currentModifiedTime = envFile.lastModified();
            if (envCache != null && currentModifiedTime <= lastModifiedTime) {
                return; // 缓存有效
            }

            // 加载.env文件
            Map<String, String> newCache = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // 跳过空行和注释
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    // 解析键值对
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex > 0) {
                        String envKey = line.substring(0, equalsIndex).trim();
                        String envValue = line.substring(equalsIndex + 1).trim();
                        // 去除可能的引号
                        if (envValue.startsWith("\"") && envValue.endsWith("\"")) {
                            envValue = envValue.substring(1, envValue.length() - 1);
                        } else if (envValue.startsWith("'") && envValue.endsWith("'")) {
                            envValue = envValue.substring(1, envValue.length() - 1);
                        }
                        newCache.put(envKey, envValue);
                    }
                }
            }

            envCache = newCache;
            lastModifiedTime = currentModifiedTime;
            log.info(".env文件加载成功: {} ({}个变量)", envFile.getAbsolutePath(), newCache.size());
        } catch (IOException e) {
            log.warn("加载.env文件失败: {}", e.getMessage());
            envCache = null;
        }
    }

    /**
     * 查找.env文件（从当前目录开始向上查找）
     */
    private static File findEnvFile() {
        // 先检查当前目录
        Path currentPath = Paths.get("").toAbsolutePath();

        // 最多向上查找3级目录
        for (int i = 0; i < 3; i++) {
            File envFile = currentPath.resolve(ENV_FILE_NAME).toFile();
            if (envFile.exists()) {
                return envFile;
            }
            // 向上级目录移动
            currentPath = currentPath.getParent();
            if (currentPath == null) {
                break;
            }
        }

        // 检查项目根目录（src/main/resources的上级）
        File projectRoot = new File(System.getProperty("user.dir"));
        File envFile = new File(projectRoot, ENV_FILE_NAME);
        if (envFile.exists()) {
            return envFile;
        }

        return null;
    }

    /**
     * 屏蔽敏感值（如API密钥）的显示
     */
    private static String maskSensitiveValue(String key, String value) {
        if (value == null) {
            return "null";
        }

        // 敏感键名列表（包含key、secret、password、token等）
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("key") || lowerKey.contains("secret") ||
            lowerKey.contains("password") || lowerKey.contains("token") ||
            lowerKey.contains("auth") || lowerKey.contains("credential")) {
            if (value.length() <= 8) {
                return "****";
            }
            return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        }
        return value;
    }

    /**
     * 重新加载.env文件（强制刷新缓存）
     */
    public static synchronized void reloadEnvFile() {
        lastModifiedTime = 0;
        loadEnvFileIfNeeded();
    }

    /**
     * 清除.env文件缓存
     */
    public static synchronized void clearCache() {
        envCache = null;
        lastModifiedTime = 0;
    }
}