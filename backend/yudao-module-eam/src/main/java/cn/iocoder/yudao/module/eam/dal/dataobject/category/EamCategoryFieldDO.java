package cn.iocoder.yudao.module.eam.dal.dataobject.category;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.eam.enums.category.EamFieldTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * EAM 分类自定义字段定义 DO
 */
@TableName(value = "eam_category_field", autoResultMap = true)
@KeySequence("eam_category_field_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamCategoryFieldDO extends BaseDO {

    /**
     * 字段编号
     */
    @TableId
    private Long id;
    /**
     * 所属分类
     */
    private Long categoryId;
    /**
     * 字段标识（英文，同分类内唯一）
     */
    private String fieldKey;
    /**
     * 字段显示名
     */
    private String fieldName;
    /**
     * 字段类型
     *
     * 枚举 {@link EamFieldTypeEnum}
     */
    private Integer fieldType;
    /**
     * 下拉选项数组（JSON），仅 fieldType=5 时使用
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;
    /**
     * 是否必填
     */
    private Boolean required;
    /**
     * 是否在管理端资产表单显示
     */
    private Boolean adminVisible;
    /**
     * 是否在员工收集表显示
     */
    private Boolean collectionVisible;
    /**
     * 员工收集表是否必填
     */
    private Boolean collectionRequired;
    /**
     * 员工收集表条件显示/必填规则；当前管理端仅保存配置，不执行
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> conditionRule;
    /**
     * 表单中的顺序
     */
    private Integer sort;

}
