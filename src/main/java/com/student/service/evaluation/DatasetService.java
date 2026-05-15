package com.student.service.evaluation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.student.entity.evaluation.EvalDataset;
import com.student.entity.evaluation.EvalDatasetItem;

import java.util.List;

/**
 * 评估数据集管理服务接口
 * 支持数据集的CRUD、导入导出、标注数据管理
 *
 * @author 系统
 * @version 1.0
 */
public interface DatasetService {

    /**
     * 创建数据集
     */
    EvalDataset createDataset(EvalDataset dataset);

    /**
     * 分页查询数据集列表
     */
    Page<EvalDataset> listDatasets(int page, int size, String domain, String status);

    /**
     * 查询数据集详情
     */
    EvalDataset getDataset(Long id);

    /**
     * 更新数据集状态
     */
    EvalDataset updateDatasetStatus(Long id, String status);

    /**
     * 删除数据集（逻辑删除）
     */
    void deleteDataset(Long id);

    /**
     * 添加数据项
     */
    EvalDatasetItem addItem(Long datasetId, EvalDatasetItem item);

    /**
     * 批量导入数据项（从JSON字符串）
     */
    int batchImportItems(Long datasetId, List<EvalDatasetItem> items);

    /**
     * 分页查询数据项
     */
    Page<EvalDatasetItem> listItems(Long datasetId, int page, int size);

    /**
     * 获取数据集的所有数据项（用于评估）
     */
    List<EvalDatasetItem> getAllItems(Long datasetId);

    /**
     * 更新数据项审核状态
     */
    EvalDatasetItem updateItemReviewStatus(Long itemId, String reviewStatus);

    /**
     * 删除数据项
     */
    void deleteItem(Long itemId);

    /**
     * 从JSON文件导入数据集
     */
    EvalDataset importFromJson(String jsonContent, String datasetName);

    /**
     * 导出数据集为JSON
     */
    String exportToJson(Long datasetId);
}
