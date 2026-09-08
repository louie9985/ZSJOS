package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import lombok.Data;

@Data
public class DeliveryClassOptionRespVO {
    private Long id;
    private String classNo;
    private String className;
    private Boolean systemClass;
    private Long categoryId;
    private String categoryName;
    private Long examScheduleId;
    private String examScheduleName;
    private Long homeroomUserId;
    private String homeroomUserName;
}
