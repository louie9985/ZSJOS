package cn.iocoder.yudao.module.eam.controller.pub.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/** 公开资产页面可修改的非系统字段。 */
@Data
public class EamPublicAssetUpdateReqVO {

    @NotNull(message = "版本不能为空")
    private Integer version;

    @NotBlank(message = "资产名称不能为空")
    private String name;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private Integer quantity;
    private LocalDate purchaseDate;
    private Integer source;
    private Long useDeptId;
    private Long useEmployeeId;
    private String location;
    private String remark;
    private Map<String, Object> extFields;

}
