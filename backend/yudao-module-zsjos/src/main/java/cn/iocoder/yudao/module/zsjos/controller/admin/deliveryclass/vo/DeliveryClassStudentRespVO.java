package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeliveryClassStudentRespVO {
    private Long serviceRelationId;
    private Long personId;
    private String personNo;
    private String studentName;
    private Long categoryId;
    private String categoryName;
    private String serviceStatus;
    private String acceptanceStatus;
    private Long ownerUserId;
    private String ownerUserName;
    private LocalDateTime activatedAt;
    private Integer version;
}
