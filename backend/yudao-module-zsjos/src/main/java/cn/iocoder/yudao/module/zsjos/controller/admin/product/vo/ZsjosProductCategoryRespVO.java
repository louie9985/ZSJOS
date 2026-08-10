package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ZsjosProductCategoryRespVO {
    private Long id;
    private Long parentId;
    private Integer level;
    private String name;
    private Integer status;
    private Integer sort;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean hasProducts;
    private List<ZsjosProductCategoryRespVO> children;
}
