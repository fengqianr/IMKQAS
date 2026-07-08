package com.student.service.rag;

import com.student.entity.DocumentChunk;

import java.util.List;

/**
 * 禁忌检测服务接口
 * 在文档预处理阶段离线抽取实体、匹配禁忌规则、标注chunk
 * 查询时直接读取标注结果，无需重复调用 HanLP
 *
 * @author 系统
 * @version 1.0
 */
public interface ContraindicationDetectionService {

    /**
     * 对一批chunk进行禁忌规则匹配，标注 hasContraindication + contraindicationInfo
     *
     * @param chunks 待标注的文档分块列表
     */
    void annotateChunks(List<DocumentChunk> chunks);

    /**
     * 检查单个文本内容，返回匹配的禁忌规则列表
     *
     * @param chunkContent 分块文本内容
     * @return 匹配的禁忌规则列表（空列表表示无匹配）
     */
    List<ContraindicationMatch> detectChunk(String chunkContent);

    /**
     * 根据查询文本生成兜底安全提示
     * 当查询涉及药物+人群但规则表未覆盖时，返回提示文本
     *
     * @param query 用户查询文本
     * @return 安全提示文本，如果不需要提示则返回 null
     */
    String buildSafetyNote(String query);

    /**
     * 刷新规则缓存（规则变更后调用）
     */
    void refreshCache();

    /** 一条禁忌规则匹配记录 */
    record ContraindicationMatch(
            String drug,
            String population,
            String type,
            String evidence,
            String description
    ) {}
}
