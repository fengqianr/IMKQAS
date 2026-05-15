package com.student.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis服务类
 * 提供常用的Redis缓存操作方法
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存
     *
     * @param key 键
     * @param value 值
     * @param expireSeconds 过期时间（秒），null表示不过期
     * @return 是否设置成功
     */
    public boolean set(String key, Object value, Long expireSeconds) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (expireSeconds != null && expireSeconds > 0) {
                ops.set(key, value, expireSeconds, TimeUnit.SECONDS);
            } else {
                ops.set(key, value);
            }
            log.debug("设置缓存成功: key={}", key);
            return true;
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 值，不存在时返回null
     */
    public Object get(String key) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            Object value = ops.get(key);
            log.debug("获取缓存: key={}, value={}", key, value != null ? "exists" : "null");
            return value;
        } catch (Exception e) {
            log.error("获取缓存失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 键
     * @return 是否删除成功
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            log.debug("删除缓存: key={}, result={}", key, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("删除缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 判断缓存是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            log.debug("检查缓存是否存在: key={}, result={}", key, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("检查缓存是否存在失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置缓存过期时间
     *
     * @param key 键
     * @param expireSeconds 过期时间（秒）
     * @return 是否设置成功
     */
    public boolean expire(String key, long expireSeconds) {
        try {
            Boolean result = redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
            log.debug("设置缓存过期时间: key={}, expireSeconds={}, result={}", key, expireSeconds, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("设置缓存过期时间失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 获取缓存剩余过期时间
     *
     * @param key 键
     * @return 剩余过期时间（秒），-1表示永不过期，-2表示键不存在
     */
    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            log.debug("获取缓存剩余过期时间: key={}, expire={}", key, expire);
            if (expire == null) {
                return -2;
            }
            return expire;
        } catch (Exception e) {
            log.error("获取缓存剩余过期时间失败: key={}", key, e);
            return -2;
        }
    }

    /**
     * 设置用户会话缓存
     *
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @param context 上下文数据
     * @return 是否设置成功
     */
    public boolean setUserSession(Long userId, Long conversationId, Object context) {
        String key = String.format("session:%d:%d", userId, conversationId);
        return set(key, context, 24 * 60 * 60L); // 24小时
    }

    /**
     * 获取用户会话缓存
     *
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 上下文数据
     */
    public Object getUserSession(Long userId, Long conversationId) {
        String key = String.format("session:%d:%d", userId, conversationId);
        return get(key);
    }

    /**
     * 删除用户会话缓存
     *
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 是否删除成功
     */
    public boolean deleteUserSession(Long userId, Long conversationId) {
        String key = String.format("session:%d:%d", userId, conversationId);
        return delete(key);
    }

    /**
     * 设置查询结果缓存
     *
     * @param queryHash 查询哈希值
     * @param result 查询结果
     * @return 是否设置成功
     */
    public boolean setQueryResult(String queryHash, Object result) {
        String key = String.format("qa:result:%s", queryHash);
        return set(key, result, 60 * 60L); // 1小时
    }

    /**
     * 获取查询结果缓存
     *
     * @param queryHash 查询哈希值
     * @return 查询结果
     */
    public Object getQueryResult(String queryHash) {
        String key = String.format("qa:result:%s", queryHash);
        return get(key);
    }

    /**
     * 设置医疗术语缓存
     *
     * @param term 医疗术语
     * @param definition 定义
     * @return 是否设置成功
     */
    public boolean setMedicalTerm(String term, Object definition) {
        String key = String.format("medical:term:%s", term);
        return set(key, definition, 7 * 24 * 60 * 60L); // 7天
    }

    /**
     * 获取医疗术语缓存
     *
     * @param term 医疗术语
     * @return 定义
     */
    public Object getMedicalTerm(String term) {
        String key = String.format("medical:term:%s", term);
        return get(key);
    }

    /**
     * 设置药物相互作用缓存
     *
     * @param drug1 药物1
     * @param drug2 药物2
     * @param interaction 相互作用信息
     * @return 是否设置成功
     */
    public boolean setDrugInteraction(String drug1, String drug2, Object interaction) {
        String key = String.format("drug:interaction:%s:%s", drug1, drug2);
        return set(key, interaction, 30 * 24 * 60 * 60L); // 30天
    }

    /**
     * 获取药物相互作用缓存
     *
     * @param drug1 药物1
     * @param drug2 药物2
     * @return 相互作用信息
     */
    public Object getDrugInteraction(String drug1, String drug2) {
        String key = String.format("drug:interaction:%s:%s", drug1, drug2);
        return get(key);
    }

    /**
     * 设置限流计数
     *
     * @param userId 用户ID
     * @param api 接口标识
     * @param count 计数
     * @param expireSeconds 过期时间（秒）
     * @return 是否设置成功
     */
    public boolean setRateLimitCount(Long userId, String api, Long count, long expireSeconds) {
        String key = String.format("rate:limit:%d:%s", userId, api);
        return set(key, count, expireSeconds);
    }

    /**
     * 获取限流计数
     *
     * @param userId 用户ID
     * @param api 接口标识
     * @return 计数，不存在时返回null
     */
    public Long getRateLimitCount(Long userId, String api) {
        String key = String.format("rate:limit:%d:%s", userId, api);
        Object value = get(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    /**
     * 递增限流计数
     *
     * @param userId 用户ID
     * @param api 接口标识
     * @param delta 增量
     * @param expireSeconds 过期时间（秒）
     * @return 递增后的值
     */
    public Long incrementRateLimitCount(Long userId, String api, long delta, long expireSeconds) {
        String key = String.format("rate:limit:%d:%s", userId, api);
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            Long result = ops.increment(key, delta);
            // 如果是首次设置，设置过期时间
            if (result != null && result == delta) {
                redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
            }
            log.debug("递增限流计数: key={}, delta={}, result={}", key, delta, result);
            return result;
        } catch (Exception e) {
            log.error("递增限流计数失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 通用递增方法
     *
     * @param key 键
     * @param delta 增量
     * @return 递增后的值
     */
    public Long increment(String key, long delta) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            Long result = ops.increment(key, delta);
            log.debug("递增: key={}, delta={}, result={}", key, delta, result);
            return result;
        } catch (Exception e) {
            log.error("递增失败: key={}", key, e);
            return null;
        }
    }

    // ========== 分布式锁方法（用于语义缓存懒加载重建） ==========

    /**
     * 尝试获取分布式锁（SETNX + 过期时间）
     *
     * @param lockKey 锁的键
     * @param ttlSeconds 锁的过期时间（秒），防止死锁
     * @return 是否获取成功
     */
    public boolean acquireLock(String lockKey, long ttlSeconds) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", ttlSeconds, TimeUnit.SECONDS);
            boolean result = acquired != null && acquired;
            log.debug("获取分布式锁: key={}, ttl={}s, result={}", lockKey, ttlSeconds, result);
            return result;
        } catch (Exception e) {
            log.error("获取分布式锁失败: key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放分布式锁（带安全校验：只释放自己持有的锁）
     *
     * @param lockKey 锁的键
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey) {
        try {
            Boolean deleted = redisTemplate.delete(lockKey);
            boolean result = deleted != null && deleted;
            log.debug("释放分布式锁: key={}, result={}", lockKey, result);
            return result;
        } catch (Exception e) {
            log.error("释放分布式锁失败: key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 设置缓存（带版本号）
     *
     * @param key 键
     * @param value 值
     * @param version 版本号
     * @param expireSeconds 过期时间（秒）
     * @return 是否设置成功
     */
    public boolean setWithVersion(String key, Object value, int version, Long expireSeconds) {
        try {
            Map<String, Object> wrapper = new java.util.HashMap<>();
            wrapper.put("v", version);
            wrapper.put("data", value);
            wrapper.put("ts", System.currentTimeMillis());
            return set(key, wrapper, expireSeconds);
        } catch (Exception e) {
            log.error("设置带版本缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 获取缓存并校验版本号
     *
     * @param key 键
     * @param expectedVersion 期望的版本号
     * @return 版本匹配时返回数据，否则返回null（视为缓存失效）
     */
    @SuppressWarnings("unchecked")
    public Object getWithVersion(String key, int expectedVersion) {
        try {
            Object raw = get(key);
            if (raw == null) return null;
            if (raw instanceof Map) {
                Map<String, Object> wrapper = (Map<String, Object>) raw;
                int storedVersion = ((Number) wrapper.get("v")).intValue();
                if (storedVersion != expectedVersion) {
                    log.debug("缓存版本不匹配: key={}, stored={}, expected={}", key, storedVersion, expectedVersion);
                    delete(key);
                    return null;
                }
                return wrapper.get("data");
            }
            return raw;
        } catch (Exception e) {
            log.error("获取带版本缓存失败: key={}", key, e);
            return null;
        }
    }
}