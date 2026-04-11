package com.student.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

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
}