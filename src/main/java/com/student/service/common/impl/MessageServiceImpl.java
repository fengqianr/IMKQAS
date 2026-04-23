package com.student.service.common.impl;

import com.student.entity.Message;
import com.student.entity.Conversation;
import com.student.mapper.MessageMapper;
import com.student.service.common.MessageService;
import com.student.service.common.ConversationService;
import com.student.exception.BusinessException;
import com.student.exception.ErrorCode;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 消息服务实现类
 * 实现消息相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final ConversationService conversationService;

    @Override
    @Transactional
    public boolean save(Message entity) {
        // 验证对话是否存在
        if (entity.getConversationId() != null) {
            Conversation conversation = conversationService.getById(entity.getConversationId());
            if (conversation == null || conversation.getDeleted() == 1) {
                // 对话不存在或已被删除，自动创建新对话
                conversation = Conversation.builder()
                        .id(entity.getConversationId()) // 使用消息中的对话ID
                        .userId(1L) // 默认用户ID，实际应获取当前用户ID
                        .title("自动创建的对话")
                        .build();
                conversationService.save(conversation);
                log.info("自动创建对话: conversationId={}, userId={}", conversation.getId(), conversation.getUserId());
            }
        } else {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR.getCode(),
                    "对话ID不能为空"
            );
        }
        return super.save(entity);
    }

    @Override
    @Transactional
    public boolean saveBatch(Collection<Message> entityList) {
        // 批量保存时逐一验证对话存在性
        Set<Long> checkedConversationIds = new HashSet<>();

        for (Message entity : entityList) {
            if (entity.getConversationId() != null) {
                Long conversationId = entity.getConversationId();

                // 如果这个对话ID已经检查过，跳过
                if (!checkedConversationIds.contains(conversationId)) {
                    Conversation conversation = conversationService.getById(conversationId);
                    if (conversation == null || conversation.getDeleted() == 1) {
                        // 对话不存在或已被删除，自动创建新对话
                        conversation = Conversation.builder()
                                .id(conversationId) // 使用消息中的对话ID
                                .userId(1L) // 默认用户ID，实际应获取当前用户ID
                                .title("自动创建的对话")
                                .build();
                        conversationService.save(conversation);
                        log.info("自动创建对话: conversationId={}, userId={}", conversation.getId(), conversation.getUserId());
                    }
                    checkedConversationIds.add(conversationId);
                }
            } else {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR.getCode(),
                        "对话ID不能为空"
                );
            }
        }
        return super.saveBatch(entityList);
    }
}