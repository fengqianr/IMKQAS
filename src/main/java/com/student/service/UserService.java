package com.student.service;

import com.student.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户服务接口
 * 提供用户相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
public interface UserService extends IService<User> {

    /**
     * 根据手机号查找用户
     *
     * @param phone 手机号
     * @return 用户对象，未找到时返回null
     */
    User findByPhone(String phone);

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户对象，未找到时返回null
     */
    User findByUsername(String username);

    /**
     * 检查手机号是否已注册
     *
     * @param phone 手机号
     * @return 是否已注册
     */
    boolean isPhoneRegistered(String phone);

    /**
     * 检查用户名是否已存在
     *
     * @param username 用户名
     * @return 是否已存在
     */
    boolean isUsernameExists(String username);
}