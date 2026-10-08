package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRepurchaseReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class RepurchaseSubmissionService {
    @Resource private RepurchaseCustomerService customers;
    @Resource private PersonMapper personMapper;
    @Resource private PersonIdentityWriteService identityWriter;
    @Resource private SalesOrderService orders;

    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, SalesOrderRepurchaseReqVO req) {
        customers.requireActor(userId);
        String name = StrUtil.trimToNull(req.getCustomerName()), mobile = StrUtil.trimToNull(req.getCustomerMobile()), wechat = StrUtil.trimToNull(req.getCustomerWechatId());
        if (name == null || mobile == null && wechat == null) throw exception(SALES_ORDER_REPURCHASE_IDENTITY_CONFLICT);
        var candidates = personMapper.selectDuplicateCandidates(mobile, wechat).stream()
                .filter(p -> Objects.equals(p.getTenantId(), TenantContextHolder.getRequiredTenantId())).toList();
        if (candidates.size() > 1) throw exception(SALES_ORDER_REPURCHASE_MULTIPLE_MATCH);
        // Contact reservations serialize no-match races. Failed identity/order validation rolls them back.
        var person = candidates.isEmpty() ? identityWriter.resolveOrCreate(name, mobile, wechat, "active") : candidates.getFirst();
        if (req.getExpectedPersonId() != null && !req.getExpectedPersonId().equals(person.getId())) throw exception(SALES_ORDER_REPURCHASE_IDENTITY_CONFLICT);
        customers.requireIdentity(person.getId(), req);
        // Cross-bean call preserves object-permission and transaction interceptors.
        return orders.createMatchedRepurchase(person.getId(), userId, req);
    }
}
