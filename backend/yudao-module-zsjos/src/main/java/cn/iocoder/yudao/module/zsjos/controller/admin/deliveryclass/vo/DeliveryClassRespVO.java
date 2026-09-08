package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;

@Data
public class DeliveryClassRespVO {
    private Long id;
    private String classNo;
    private String className;
    private Boolean systemClass;
    private Long productId;
    private String productNameSnapshot;
    private Map<String, String> selectedAttrs;
    private List<ProductSpecVO> selectedSpecs;
    private List<ExamProductScopeRespVO.Sku> selectedSkus;
    private Long categoryId;
    private String categoryNameSnapshot;
    private String categoryPathSnapshot;
    private Long examScheduleId;
    private String examScheduleSnapshot;
    private Long homeroomUserId;
    private String homeroomUserNameSnapshot;
    private Long deptId;
    private String deptNameSnapshot;
    private String status;
    private Integer studentCount;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
