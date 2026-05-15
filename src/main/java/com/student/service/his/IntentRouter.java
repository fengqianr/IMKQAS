package com.student.service.his;

/**
 * 意图路由器接口
 * 用户输入 → classify() → IntentType
 *
 * @author 系统
 * @version 1.0
 */
public interface IntentRouter {

    /**
     * 对用户输入进行意图分类
     *
     * @param userInput 用户输入文本
     * @return 意图类型
     */
    IntentType classify(String userInput);
}
