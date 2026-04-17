package com.student.service.triage.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Mockito测试基类
 * 提供统一的Mockito配置，解决严格模式和异步测试问题
 *
 * @author 系统生成
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseMockitoTest {

    /**
     * 初始化Mockito注解
     * 确保所有@Mock、@InjectMocks等注解正确初始化
     */
    @BeforeEach
    void setUpMockito() {
        MockitoAnnotations.openMocks(this);
    }
}