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

    @Override
    public java.util.List<Conversation> listDeleted(Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Conversation> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        // 跳过逻辑删除过滤，查询已删除记录
        wrapper.eq("deleted", 1);
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("updated_at");
        return baseMapper.selectList(wrapper);
    }

    @Override
    public boolean restoreConversation(Long id) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setDeleted(0);
        return baseMapper.updateById(conversation) > 0;
    }
}