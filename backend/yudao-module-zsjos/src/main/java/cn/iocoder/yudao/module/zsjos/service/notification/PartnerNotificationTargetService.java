package cn.iocoder.yudao.module.zsjos.service.notification;

import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class PartnerNotificationTargetService {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadMapper leadMapper;

    /** Caller must first authorize the persisted message; partnerId comes only from the authenticated account. */
    public String orderLeadPath(Long orderId, Long partnerId) {
        if (orderId == null || partnerId == null) return null;
        var order = orderMapper.selectById(orderId);
        if (order == null || order.getLeadId() == null) return null;
        var lead = leadMapper.selectById(order.getLeadId());
        if (lead == null || !Objects.equals(partnerId, lead.getPartnerId())) return null;
        return "/lead/" + lead.getId();
    }
}
