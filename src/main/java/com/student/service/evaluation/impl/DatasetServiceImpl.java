package com.student.service.evaluation.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.student.entity.evaluation.EvalDataset;
import com.student.entity.evaluation.EvalDatasetItem;
import com.student.mapper.evaluation.EvalDatasetItemMapper;
import com.student.mapper.evaluation.EvalDatasetMapper;
import com.student.service.evaluation.DatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评估数据集管理服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DatasetServiceImpl implements DatasetService {

    private final EvalDatasetMapper datasetMapper;
    private final EvalDatasetItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public EvalDataset createDataset(EvalDataset dataset) {
        if (dataset.getVersion() == null) {
            dataset.setVersion("1.0");
        }
        if (dataset.getStatus() == null) {
            dataset.setStatus(EvalDataset.STATUS_DRAFT);
        }
        if (dataset.getTotalItems() == null) {
            dataset.setTotalItems(0);
        }
        datasetMapper.insert(dataset);
        log.info("创建评估数据集: {} (v{})", dataset.getName(), dataset.getVersion());
        return dataset;
    }

    @Override
    public Page<EvalDataset> listDatasets(int page, int size, String domain, String status) {
        LambdaQueryWrapper<EvalDataset> wrapper = new LambdaQueryWrapper<>();
        if (domain != null && !domain.isEmpty()) {
            wrapper.eq(EvalDataset::getDomain, domain);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EvalDataset::getStatus, status);
        }
        wrapper.orderByDesc(EvalDataset::getCreatedAt);
        return datasetMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public EvalDataset getDataset(Long id) {
        return datasetMapper.selectById(id);
    }

    @Override
    @Transactional
    public EvalDataset updateDatasetStatus(Long id, String status) {
        EvalDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + id);
        }
        dataset.setStatus(status);
        datasetMapper.updateById(dataset);
        log.info("数据集 {} 状态更新为: {}", id, status);
        return dataset;
    }

    @Override
    @Transactional
    public void deleteDataset(Long id) {
        datasetMapper.deleteById(id);
        // 级联删除关联的数据项
        LambdaQueryWrapper<EvalDatasetItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDatasetItem::getDatasetId, id);
        itemMapper.delete(wrapper);
        log.info("删除评估数据集: {}", id);
    }

    @Override
    @Transactional
    public EvalDatasetItem addItem(Long datasetId, EvalDatasetItem item) {
        item.setDatasetId(datasetId);
        // 自动设置序号
        if (item.getItemIndex() == null) {
            LambdaQueryWrapper<EvalDatasetItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EvalDatasetItem::getDatasetId, datasetId);
            long count = itemMapper.selectCount(wrapper);
            item.setItemIndex((int) count + 1);
        }
        if (item.getReviewStatus() == null) {
            item.setReviewStatus("PENDING");
        }
        itemMapper.insert(item);

        // 更新数据集的total_items计数
        EvalDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset != null) {
            dataset.setTotalItems(dataset.getTotalItems() + 1);
            datasetMapper.updateById(dataset);
        }

        log.debug("添加数据项到数据集 {}: {}", datasetId, item.getItemIndex());
        return item;
    }

    @Override
    @Transactional
    public int batchImportItems(Long datasetId, List<EvalDatasetItem> items) {
        int count = 0;
        for (EvalDatasetItem item : items) {
            addItem(datasetId, item);
            count++;
        }
        log.info("批量导入 {} 条数据项到数据集 {}", count, datasetId);
        return count;
    }

    @Override
    public Page<EvalDatasetItem> listItems(Long datasetId, int page, int size) {
        LambdaQueryWrapper<EvalDatasetItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDatasetItem::getDatasetId, datasetId);
        wrapper.orderByAsc(EvalDatasetItem::getItemIndex);
        return itemMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<EvalDatasetItem> getAllItems(Long datasetId) {
        LambdaQueryWrapper<EvalDatasetItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDatasetItem::getDatasetId, datasetId);
        wrapper.orderByAsc(EvalDatasetItem::getItemIndex);
        return itemMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public EvalDatasetItem updateItemReviewStatus(Long itemId, String reviewStatus) {
        EvalDatasetItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("数据项不存在: " + itemId);
        }
        item.setReviewStatus(reviewStatus);
        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional
    public void deleteItem(Long itemId) {
        EvalDatasetItem item = itemMapper.selectById(itemId);
        if (item != null) {
            itemMapper.deleteById(itemId);
            // 更新数据集计数
            EvalDataset dataset = datasetMapper.selectById(item.getDatasetId());
            if (dataset != null && dataset.getTotalItems() > 0) {
                dataset.setTotalItems(dataset.getTotalItems() - 1);
                datasetMapper.updateById(dataset);
            }
        }
    }

    @Override
    @Transactional
    public EvalDataset importFromJson(String jsonContent, String datasetName) {
        try {
            EvalDataset dataset = EvalDataset.builder()
                    .name(datasetName)
                    .version("1.0")
                    .source(EvalDataset.SOURCE_MANUAL)
                    .status(EvalDataset.STATUS_DRAFT)
                    .totalItems(0)
                    .build();
            datasetMapper.insert(dataset);

            // 解析 JSON 数组，每项转成 EvalDatasetItem
            @SuppressWarnings("unchecked")
            List<EvalDatasetItem> items = objectMapper.readValue(jsonContent,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, EvalDatasetItem.class));
            for (EvalDatasetItem item : items) {
                item.setDatasetId(dataset.getId());
                addItem(dataset.getId(), item);
            }

            log.info("从JSON导入数据集: {} ({}条)", datasetName, items.size());
            return dataset;
        } catch (Exception e) {
            log.error("导入JSON数据集失败: {}", e.getMessage(), e);
            throw new RuntimeException("导入JSON数据集失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String exportToJson(Long datasetId) {
        try {
            List<EvalDatasetItem> items = getAllItems(datasetId);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);
        } catch (Exception e) {
            log.error("导出数据集JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("导出数据集JSON失败: " + e.getMessage(), e);
        }
    }
}
