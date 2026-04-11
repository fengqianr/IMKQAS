package com.student.mapper;

import com.student.entity.DocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档分块Mapper接口
 * 提供文档分块数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
    // 如果需要复杂查询，可以在此定义方法，并对应 XML 文件
}