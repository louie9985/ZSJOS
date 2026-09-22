package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;

@Service
public class LeadOrderNotificationTargetService {
    @Resource private SalesOrderMapper salesOrderMapper;
    @Resource private LeadManagementService leadManagementService;

    public LeadManagementRespVO getLead(Long orderId, Long userId) {
        var order = salesOrderMapper.selectById(orderId);
        if (order == null || order.getLeadId() == null) throw exception(LEAD_NOT_EXISTS);
        // Historical notifications store an order ID. Resolve only its Lead through the
        // proxied Lead service so object authorization applies without exposing order data.
        return leadManagementService.getLead(order.getLeadId(), userId);
    }
}
