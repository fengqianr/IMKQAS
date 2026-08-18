package com.imkqas.mapper;

import com.imkqas.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息Mapper接口
 * 提供消息数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 物理删除指定对话下的所有消息（供对话彻底删除时清理）
     */
    @Delete("DELETE FROM messages WHERE conversation_id = #{conversationId}")
    int physicalDeleteByConversationId(@Param("conversationId") Long conversationId);
}