package com.imkqas.mapper;

import com.imkqas.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    /**
     * 查询回收站中的已删除对话
     * 使用原生 SQL 绕过 MyBatis Plus 对 @TableLogic 字段的 deleted=0 自动过滤，
     * 否则与显式条件 deleted=1 冲突，永远查不到已删除记录。
     *
     * @param userId 用户ID（可选，为 null 时查询全部用户的回收站）
     * @return 已删除的对话列表，按更新时间倒序
     */
    @Select("<script>SELECT * FROM conversations WHERE deleted = 1" +
            "<if test='userId != null'> AND user_id = #{userId}</if>" +
            " ORDER BY updated_at DESC</script>")
    List<Conversation> selectDeleted(@Param("userId") Long userId);
}