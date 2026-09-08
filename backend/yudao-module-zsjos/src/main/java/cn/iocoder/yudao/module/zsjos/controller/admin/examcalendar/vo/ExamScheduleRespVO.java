package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamScheduleRespVO {
    private Long id;
    private String scheduleType;
    private LocalDate exactDate;
    private LocalDate roughStartDate;
    private LocalDate roughEndDate;
    private Long categoryId;
    private Long productId;
    private String productNameSnapshot;
    private String scheduleName;
    private java.util.Map<String, String> selectedAttrs;
    private List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> selectedSpecs;
    private List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO.Sku> frozenSkus;
    private String categoryNameSnapshot;
    private List<ZsjosProductCategoryPathNodeVO> categoryPathSnapshot;
    private String recordStatus;
    private String displayStatus;
    private String remark;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
