package com.student.service.his;

/**
 * 用户输入意图类型枚举
 *
 * @author 系统
 * @version 1.0
 */
public enum IntentType {

    /**
     * 知识查询 — 走RAG路径，安全机制约束
     */
    KNOWLEDGE_QUERY,

    /**
     * 数据采集 — 走问卷路径，绕过知识库
     */
    DATA_COLLECTION,

    /**
     * 混合型 — RAG回答 + 尾部建议填表
     */
    MIXED
}
