package com.imkqas.service.common.impl;

import com.imkqas.entity.Conversation;
import com.imkqas.mapper.ConversationMapper;
import com.imkqas.mapper.MessageMapper;
import com.imkqas.service.common.ConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话会话服务实现类
 * 实现对话会话相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    private final MessageMapper messageMapper;

    @Override
    public java.util.List<Conversation> listDeleted(Long userId) {
        // 使用原生 SQL 绕过 MyBatis Plus 逻辑删除自动过滤（selectList 会追加 deleted=0，
        // 与手动条件 deleted=1 冲突导致永远查不到），查询回收站中已删除的记录
        return baseMapper.selectDeleted(userId);
    }

    @Override
    public boolean restoreConversation(Long id) {
        return baseMapper.restoreById(id) > 0;
    }

    @Override
    @Transactional
    public boolean deletePermanently(Long id) {
        // 物理删除该对话下的所有消息，避免孤儿数据
        messageMapper.physicalDeleteByConversationId(id);
        // 物理删除对话本身（绕过 @TableLogic 软删除）
        return baseMapper.physicalDeleteById(id) > 0;
    }
}