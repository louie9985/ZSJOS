package cn.iocoder.yudao.module.eam.dal.dataobject.asset;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.math.BigDecimal;
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
public class EamAssetDO extends TenantBaseDO {

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
     * 分类管理模式快照：1 单件，2 批量
     */
    private Integer managementMode;
    /**
     * 资产数量；单件资产固定为 1
     */
    private Integer quantity;
    /**
     * 计量单位快照
     */
    private String unit;
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
    /** 原值 */
    private BigDecimal originalValue;
    /** 净值 */
    private BigDecimal netValue;
    /**
     * 购入日期
     */
    private LocalDate purchaseDate;
    /** 资产来源字典值 */
    private Integer source;
    /** 资产来源标签快照 */
    private String sourceLabelSnapshot;
    /** 保修到期日 */
    private LocalDate warrantyDate;
    /**
     * 使用部门
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long useDeptId;
    /**
     * 使用人
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long useEmployeeId;
    /**
     * 使用员工姓名快照；员工匹配失败或历史名称变化时保留
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String useEmployeeNameSnapshot;
    /**
     * 直属上级员工
     */
    private Long supervisorEmployeeId;
    /**
     * 直属上级姓名快照
     */
    private String supervisorNameSnapshot;
    /**
     * 入司日期
     */
    private LocalDate joinDate;
    /**
     * 使用人承诺是否确认
     */
    private Boolean commitmentAccepted;
    /**
     * 承诺日期
     */
    private LocalDate commitmentDate;
    /**
     * 存放地点
     */
    private String location;
    /** 预计使用年限，单位月 */
    private Integer expectedLife;
    /**
     * 备注
     */
    private String remark;
    /**
     * 附件地址数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileUrls;
    /**
     * 分类自定义字段扩展值 JSON
     * 结构如 {"account":"xxx", "platform":"移动"}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extFields;
    /** 下拉扩展字段的标签快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldLabels;
    /** 下拉扩展字段的字典类型快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extFieldDictTypes;
    /**
     * 状态变更前的状态（用于维修完成/报废驳回/解冻 恢复原状态）
     */
    private Integer previousStatus;

    /** 乐观锁版本，公开编辑与后台编辑共用。 */
    private Integer version;

}
