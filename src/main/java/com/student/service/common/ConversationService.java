package com.student.service.common;

import com.student.entity.Conversation;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 对话会话服务接口
 * 提供对话会话相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 查询已删除（回收站）的对话列表
     * @param userId 用户ID，为null时查询所有
     * @return 已删除的对话列表
     */
    java.util.List<Conversation> listDeleted(Long userId);

    /**
     * 从回收站恢复对话
     * @param id 对话ID
     * @return 是否恢复成功
     */
    boolean restoreConversation(Long id);
}