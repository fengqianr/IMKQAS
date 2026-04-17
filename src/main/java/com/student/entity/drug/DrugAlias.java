package com.student.entity.drug;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 药品别名实体类
 * 对应数据库表: drug_aliases
 *
 * @author 系统
 * @version 1.0
 */
@TableName("drug_aliases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugAlias {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("drug_id")
    private Long drugId;

    /**
     * 别名类型枚举
     */
    public enum AliasType {
        BRAND_NAME("商品名"),
        COMMON_NAME("通用名"),
        ABBREVIATION("缩写"),
        TRADE_NAME("商标名"),
        OTHER("其他");

        private final String description;

        AliasType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @TableField("alias_type")
    private AliasType aliasType;

    @TableField("alias_name")
    private String aliasName;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /**
     * 获取别名类型描述
     * @return 别名类型描述
     */
    public String getAliasTypeDescription() {
        return aliasType != null ? aliasType.getDescription() : "";
    }
}