package com.student.mapper;

import com.student.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档Mapper接口
 * 提供文档数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
    // 如果需要复杂查询，可以在此定义方法，并对应 XML 文件
}