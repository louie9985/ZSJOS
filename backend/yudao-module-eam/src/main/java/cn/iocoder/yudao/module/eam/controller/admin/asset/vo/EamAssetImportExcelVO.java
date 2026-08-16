package cn.iocoder.yudao.module.eam.controller.admin.asset.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EAM 资产导入 Excel VO
 *
 * 导入模板只覆盖基础字段。分类自定义字段依赖分类配置，行与行之间列不一致，
 * 无法用固定表头表达，需在导入后到资产详情补录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EamAssetImportExcelVO {

    private String name;

    private String categoryCode;

    private String brand;

    private String specification;

    private String sn;

    private String barcode;

    private BigDecimal originalValue;

    private BigDecimal netValue;

    private LocalDate purchaseDate;

    private LocalDate warrantyDate;

    private String location;

    private Integer expectedLife;

    private String remark;

}
