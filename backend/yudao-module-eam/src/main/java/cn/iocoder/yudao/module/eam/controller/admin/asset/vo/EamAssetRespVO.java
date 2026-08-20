package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - EAM 资产 Response VO")
@Data
public class EamAssetRespVO {

    @Schema(description = "资产编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "资产业务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "IT-2026-0001")
    private String assetCode;

    @Schema(description = "资产名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "MacBook Pro 14")
    private String name;

    @Schema(description = "分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long categoryId;

    @Schema(description = "管理模式：1 单件，2 批量")
    private Integer managementMode;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "计量单位")
    private String unit;

    @Schema(description = "分类名称", example = "设备资产")
    private String categoryName;

    @Schema(description = "资产状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "品牌型号", example = "Apple M3 Pro")
    private String brand;

    @Schema(description = "规格参数", example = "18G/512G")
    private String specification;

    @Schema(description = "序列号", example = "C02XY1234")
    private String sn;

    @Schema(description = "条码", example = "6901234567890")
    private String barcode;

    @Schema(description = "购入日期", example = "2026-01-15")
    private LocalDate purchaseDate;

    @Schema(description = "使用部门编号", example = "100")
    private Long useDeptId;

    @Schema(description = "使用部门名称", example = "研发部")
    private String useDeptName;

    @Schema(description = "使用人编号", example = "1")
    private Long useUserId;

    @Schema(description = "使用人名称", example = "张三")
    private String useUserName;

    @Schema(description = "使用人姓名快照", example = "张三")
    private String useUserNameSnapshot;

    @Schema(description = "直属上级用户编号", example = "2")
    private Long supervisorUserId;

    @Schema(description = "直属上级姓名快照", example = "李四")
    private String supervisorNameSnapshot;

    @Schema(description = "入司日期", example = "2024-08-01")
    private LocalDate joinDate;

    @Schema(description = "使用人承诺是否确认", example = "true")
    private Boolean commitmentAccepted;

    @Schema(description = "承诺日期", example = "2026-08-17")
    private LocalDate commitmentDate;

    @Schema(description = "存放地点", example = "总部三楼研发区")
    private String location;

    @Schema(description = "备注", example = "研发部专用")
    private String remark;

    @Schema(description = "附件地址数组")
    private List<String> fileUrls;

    @Schema(description = "分类自定义字段值")
    private Map<String, Object> extFields;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
