package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo;

import java.util.List;

public record DeliveryClassFilterContextRespVO(List<DeliveryClassProjectRespVO> projects,
        List<Homeroom> homerooms, DeliveryClassRespVO pendingClass) {
    public record Homeroom(Long id, String name, String deptName) {}
}
