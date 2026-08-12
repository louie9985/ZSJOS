package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesOrderStatusCountsRespVO {
    private Long total;
    private Long pendingApproval;
    private Long revisionRequired;
    private Long effective;
}
