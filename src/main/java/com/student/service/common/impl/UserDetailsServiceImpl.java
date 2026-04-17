package com.student.service.common.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.student.entity.User;
import com.student.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security UserDetailsService实现
 * 用于根据用户名加载用户详情
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * 根据用户名加载用户详情
     *
     * @param username 用户名
     * @return UserDetails对象
     * @throws UsernameNotFoundException 用户未找到异常
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载用户详情: username={}", username);

        // 构建查询条件
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        queryWrapper.eq("deleted", 0); // 未删除的用户

        // 查询用户
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            log.warn("用户未找到: username={}", username);
            throw new UsernameNotFoundException("用户未找到: " + username);
        }

        log.debug("用户加载成功: username={}, role={}", user.getUsername(), user.getRole());
        return user;
    }

    /**
     * 根据手机号加载用户详情
     * 用于手机号登录场景
     *
     * @param phone 手机号
     * @return UserDetails对象
     * @throws UsernameNotFoundException 用户未找到异常
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserByPhone(String phone) throws UsernameNotFoundException {
        log.debug("根据手机号加载用户详情: phone={}", phone);

        // 构建查询条件
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        queryWrapper.eq("deleted", 0); // 未删除的用户

        // 查询用户
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            log.warn("用户未找到: phone={}", phone);
            throw new UsernameNotFoundException("用户未找到: " + phone);
        }

        log.debug("用户加载成功: phone={}, username={}, role={}", phone, user.getUsername(), user.getRole());
        return user;
    }
}