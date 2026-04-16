package com.student.service.common.impl;

import com.student.entity.Message;
import com.student.mapper.MessageMapper;
import com.student.service.common.MessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 消息服务实现类
 * 实现消息相关的业务操作
 *
 * @author 系统
 * @version 1.0
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
    // 如果有额外的业务逻辑，在这里实现
}