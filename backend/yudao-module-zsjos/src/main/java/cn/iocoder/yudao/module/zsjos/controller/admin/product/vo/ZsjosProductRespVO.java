package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

@Data
public class ZsjosProductRespVO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private List<ZsjosProductCategoryPathNodeVO> categoryPath;
    private Long level1CategoryId;
    private String level1CategoryName;
    private Long level2CategoryId;
    private String level2CategoryName;
    private String productRef;
    private String name;
    private String subtitle;
    private String description;
    private String targetAudience;
    private String studyDuration;
    private String studyMode;
    private String coverImage;
    private BigDecimal validCashbackAmount;
    private BigDecimal dealCashbackRate;
    private Integer status;
    private Integer sort;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
