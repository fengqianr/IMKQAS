package com.student.service;

import java.util.List;

/**
 * 嵌入模型服务接口
 * 负责将文本转换为向量表示，支持本地模型和远程API两种部署方式
 *
 * @author 系统
 * @version 1.0
 */
public interface EmbeddingService {

    /**
     * 将单个文本转换为向量
     *
     * @param text 输入文本
     * @return 向量表示（浮点数列表）
     */
    List<Float> embed(String text);

    /**
     * 批量将文本转换为向量
     *
     * @param texts 输入文本列表
     * @return 向量列表，每个文本对应一个向量
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     *
     * @return 向量维度（如1024）
     */
    int getDimension();

    /**
     * 验证向量维度是否符合预期
     *
     * @param vector 待验证向量
     * @return 是否有效
     * @throws IllegalArgumentException 如果向量维度不正确
     */
    boolean validateVector(List<Float> vector);

    /**
     * 获取服务状态
     *
     * @return 服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取部署方式
     *
     * @return "local" 或 "api"
     */
    String getDeploymentType();
}