package com.student.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

/**
 * 验证码服务（模拟实现）
 * 实际生产环境应集成短信服务商API
 *
 * @author 系统
 * @version 1.0
 */
@Service
public class CodeService {

    // 内存存储验证码，key: 手机号, value: 验证码
    private final Map<String, String> codeStore = new ConcurrentHashMap<>();
    // 验证码有效期（毫秒）
    private static final long CODE_EXPIRATION = 5 * 60 * 1000; // 5分钟
    // 验证码发送记录，用于限制发送频率
    private final Map<String, Long> sendTimeStore = new ConcurrentHashMap<>();
    // 最小发送间隔（毫秒）
    private static final long MIN_SEND_INTERVAL = 60 * 1000; // 1分钟

    private final Random random = new Random();

    /**
     * 生成随机6位数字验证码
     *
     * @return 6位数字验证码字符串
     */
    private String generateCode() {
        return String.format("%06d", random.nextInt(1000000));
    }

    /**
     * 发送验证码（模拟）
     *
     * @param phone 手机号
     * @return 是否发送成功
     */
    public boolean sendCode(String phone) {
        // 检查发送频率
        Long lastSendTime = sendTimeStore.get(phone);
        long currentTime = System.currentTimeMillis();
        if (lastSendTime != null && currentTime - lastSendTime < MIN_SEND_INTERVAL) {
            return false; // 发送过于频繁
        }

        // 生成验证码
        String code = generateCode();

        // 存储验证码和发送时间
        codeStore.put(phone, code);
        sendTimeStore.put(phone, currentTime);

        // 模拟发送过程（实际应调用短信API）
        System.out.println("[模拟短信] 发送验证码到 " + phone + ": " + code);

        return true;
    }

    /**
     * 验证验证码
     *
     * @param phone 手机号
     * @param code 用户输入的验证码
     * @return 是否验证通过
     */
    public boolean verifyCode(String phone, String code) {
        String storedCode = codeStore.get(phone);
        if (storedCode == null) {
            return false; // 验证码不存在或已过期
        }

        // 验证码匹配（不区分大小写）
        boolean matched = storedCode.equals(code);

        // 验证后删除验证码（一次性使用）
        if (matched) {
            codeStore.remove(phone);
        }

        return matched;
    }

    /**
     * 清理过期的验证码
     * 此方法应定期调用（如通过定时任务）
     */
    public void cleanupExpiredCodes() {
        long currentTime = System.currentTimeMillis();
        // 简化实现：实际生产环境应使用Redis并设置TTL
        // 这里仅清理超过有效期的验证码
        codeStore.forEach((phone, code) -> {
            Long sendTime = sendTimeStore.get(phone);
            if (sendTime != null && currentTime - sendTime > CODE_EXPIRATION) {
                codeStore.remove(phone);
                sendTimeStore.remove(phone);
            }
        });
    }

    /**
     * 获取验证码剩余有效时间（秒）
     *
     * @param phone 手机号
     * @return 剩余有效时间（秒），-1表示验证码不存在
     */
    public long getRemainingTime(String phone) {
        Long sendTime = sendTimeStore.get(phone);
        if (sendTime == null) {
            return -1;
        }

        long elapsed = System.currentTimeMillis() - sendTime;
        if (elapsed > CODE_EXPIRATION) {
            return 0;
        }

        return (CODE_EXPIRATION - elapsed) / 1000;
    }

    /**
     * 检查是否可发送验证码（频率限制）
     *
     * @param phone 手机号
     * @return 是否可发送
     */
    public boolean canSendCode(String phone) {
        Long lastSendTime = sendTimeStore.get(phone);
        if (lastSendTime == null) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastSendTime;
        return elapsed >= MIN_SEND_INTERVAL;
    }

    /**
     * 获取下一次可发送的剩余时间（秒）
     *
     * @param phone 手机号
     * @return 剩余时间（秒），0表示可立即发送
     */
    public long getNextSendRemainingTime(String phone) {
        Long lastSendTime = sendTimeStore.get(phone);
        if (lastSendTime == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - lastSendTime;
        if (elapsed >= MIN_SEND_INTERVAL) {
            return 0;
        }

        return (MIN_SEND_INTERVAL - elapsed) / 1000;
    }
}