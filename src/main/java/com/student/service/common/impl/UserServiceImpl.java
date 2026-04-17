package com.student.service.common.impl;

import com.student.entity.User;
import com.student.mapper.UserMapper;
import com.student.service.common.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.Serializable;

/**
 * 用户服务实现类
 * 实现用户相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User findByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        queryWrapper.eq(User::getDeleted, 0); // 未删除的用户
        return getOne(queryWrapper);
    }

    @Override
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        queryWrapper.eq(User::getDeleted, 0); // 未删除的用户
        return getOne(queryWrapper);
    }

    @Override
    public boolean isPhoneRegistered(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        queryWrapper.eq(User::getDeleted, 0); // 未删除的用户
        return count(queryWrapper) > 0;
    }

    @Override
    public boolean isUsernameExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        queryWrapper.eq(User::getDeleted, 0); // 未删除的用户
        return count(queryWrapper) > 0;
    }

    /**
     * 根据ID查找用户（重写以包含逻辑删除检查）
     *
     * @param id 用户ID
     * @return 用户对象
     */
    @Override
    public User getById(Serializable id) {
        if (id == null) {
            return null;
        }

        // 将参数转换为Long类型进行查询
        Long userId;
        try {
            userId = Long.valueOf(id.toString());
        } catch (NumberFormatException e) {
            return null;
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userId);
        queryWrapper.eq(User::getDeleted, 0); // 未删除的用户
        return getOne(queryWrapper);
    }
}