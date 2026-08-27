package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - EAM 资产创建/更新 Request VO")
@Data
public class EamAssetSaveReqVO {

    @Schema(description = "资产编号（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "资产业务编号；导入时可沿用已有资产标签", example = "ZSJ-001")
    private String assetCode;

    @Schema(description = "资产名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "MacBook Pro 14")
    @NotBlank(message = "资产名称不能为空")
    private String name;

    @Schema(description = "分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Schema(description = "数量；单件资产固定为 1", example = "1")
    private Integer quantity;

    @Schema(description = "品牌型号", example = "Apple M3 Pro")
    private String brand;

    @Schema(description = "规格参数", example = "18G/512G")
    private String specification;

    @Schema(description = "序列号", example = "C02XY1234")
    private String sn;

    @Schema(description = "条码", example = "6901234567890")
    private String barcode;

    @Schema(description = "原值")
    private BigDecimal originalValue;

    @Schema(description = "净值")
    private BigDecimal netValue;

    @Schema(description = "购入日期", example = "2026-01-15")
    private LocalDate purchaseDate;

    @Schema(description = "资产来源字典值")
    private Integer source;

    @Schema(description = "保修到期日")
    private LocalDate warrantyDate;

    @Schema(description = "使用部门编号", example = "100")
    private Long useDeptId;

    @Schema(description = "使用人编号", example = "1")
    private Long useEmployeeId;

    @Schema(description = "存放地点", example = "总部三楼研发区")
    private String location;

    @Schema(description = "预计使用年限，单位月")
    private Integer expectedLife;

    @Schema(description = "备注", example = "研发部专用")
    private String remark;

    @Schema(description = "附件地址数组")
    private List<String> fileUrls;

    @Schema(description = "分类自定义字段值", example = "{\"account\":\"admin\"}")
    private Map<String, Object> extFields;

}
