package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import lombok.AllArgsConstructor;
import lombok.Data;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;
import java.util.List;

@Data
@AllArgsConstructor
public class LeadProductSimpleRespVO {
    private String productRef;
    private String name;
    private Long categoryId;
    private String categoryName;
    private List<ZsjosProductCategoryPathNodeVO> categoryPath;
    private Long level1CategoryId;
    private String level1CategoryName;
    private Long level2CategoryId;
    private String level2CategoryName;
}
