package cn.iocoder.yudao.module.eam.dal.dataobject.coderule;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * EAM 资产编号规则 DO
 */
@TableName("eam_code_rule")
@KeySequence("eam_code_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamCodeRuleDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 适用分类 ID，NULL 表示全局默认规则
     */
    private Long categoryId;
    /**
     * 固定前缀
     */
    private String prefix;
    /**
     * 是否拼接分类 code
     */
    private Boolean useCategoryCode;
    /**
     * 日期部分格式，如 yyyy、yyyyMM，空则不含日期
     */
    private String dateFormat;
    /**
     * 流水号位数，默认 4
     */
    private Integer serialLength;
    /**
     * 分隔符，默认 -
     */
    @TableField("`separator`")
    private String separator;
    /**
     * 当前流水号（该规则下已分配的最大流水号）
     */
    private Long currentSerial;

}
