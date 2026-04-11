package com.student.mapper;

import com.student.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话Mapper接口
 * 提供对话会话数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
    // 如果需要复杂查询，可以在此定义方法，并对应 XML 文件
}