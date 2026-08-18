package com.imkqas.mapper;

import com.imkqas.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 对话会话Mapper接口
 * 提供对话会话数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 物理删除对话（绕过 @TableLogic 软删除，供回收站彻底删除使用）
     */
    @Delete("DELETE FROM conversations WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 恢复软删除的对话（绕过 @TableLogic 的 deleted=0 过滤，直接置回 0）
     */
    @Update("UPDATE conversations SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}