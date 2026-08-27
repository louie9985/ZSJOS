package cn.iocoder.yudao.module.eam.dal.dataobject.category;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * EAM 资产分类 DO
 */
@TableName("eam_category")
@KeySequence("eam_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamCategoryDO extends BaseDO {

    /**
     * 分类编号
     */
    @TableId
    private Long id;
    /**
     * 父分类编号，根为 0
     */
    private Long parentId;
    /**
     * 分类名称
     */
    private String name;
    /**
     * 分类编码，用于资产编号前缀
     */
    private String code;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 状态：0 开启 / 1 关闭
     */
    private Integer status;
    /**
     * 管理模式：1 单件，2 批量
     */
    private Integer managementMode;
    /**
     * 交付模式：1 实物入库，2 数字交付；子分类为空表示继承。
     */
    private Integer deliveryMode;
    /**
     * 持有模式：1 消耗型，2 需归还型；子分类为空表示继承。
     */
    private Integer custodyMode;
    /**
     * 默认计量单位
     */
    private String unit;
    /**
     * 备注
     */
    private String remark;

}
