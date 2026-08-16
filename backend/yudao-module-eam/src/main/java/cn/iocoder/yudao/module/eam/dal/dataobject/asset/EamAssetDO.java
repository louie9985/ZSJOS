package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.mybatis.core.type.StringListTypeHandler;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * EAM 资产卡片 DO
 */
@TableName(value = "eam_asset", autoResultMap = true)
@KeySequence("eam_asset_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamAssetDO extends BaseDO {

    /**
     * 资产编号（主键）
     */
    @TableId
    private Long id;
    /**
     * 资产编号（业务编号，自动生成）
     */
    private String assetCode;
    /**
     * 资产名称
     */
    private String name;
    /**
     * 资产分类
     */
    private Long categoryId;
    /**
     * 资产状态
     *
     * 枚举 {@link EamAssetStatusEnum}
     */
    private Integer status;
    /**
     * 品牌型号
     */
    private String brand;
    /**
     * 规格参数
     */
    private String specification;
    /**
     * 序列号
     */
    private String sn;
    /**
     * 条码
     */
    private String barcode;
    /**
     * 原值
     */
    private BigDecimal originalValue;
    /**
     * 净值
     */
    private BigDecimal netValue;
    /**
     * 购入日期
     */
    private LocalDate purchaseDate;
    /**
     * 来源，字典 eam_asset_source
     */
    private Integer source;
    /**
     * 保修到期日
     */
    private LocalDate warrantyDate;
    /**
     * 使用部门
     */
    private Long useDeptId;
    /**
     * 使用人
     */
    private Long useUserId;
    /**
     * 存放地点
     */
    private String location;
    /**
     * 预计使用年限（月）
     */
    private Integer expectedLife;
    /**
     * 备注
     */
    private String remark;
    /**
     * 附件地址数组
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;
    /**
     * 分类自定义字段扩展值 JSON
     * 结构如 {"account":"xxx", "platform":"移动"}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extFields;
    /**
     * 状态变更前的状态（用于维修完成/报废驳回/解冻 恢复原状态）
     */
    private Integer previousStatus;

}
