package com.student.mapper;

import com.student.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 * 提供用户数据的数据库操作
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 如果需要复杂查询，可以在此定义方法，并对应 XML 文件
}