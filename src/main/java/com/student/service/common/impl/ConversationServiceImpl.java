package com.student.service.common.impl;

import com.student.entity.Conversation;
import com.student.mapper.ConversationMapper;
import com.student.service.common.ConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 对话会话服务实现类
 * 实现对话会话相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {
    // 如果有额外的业务逻辑，在这里实现
}