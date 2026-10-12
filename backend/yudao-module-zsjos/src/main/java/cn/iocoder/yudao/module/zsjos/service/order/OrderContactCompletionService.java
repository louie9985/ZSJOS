package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Only the authorized order command supplies the Lead context; sharing a Person is not a Lead grant. */
@Service
public class OrderContactCompletionService {
    @Resource private LeadMapper leadMapper;
    @Resource private PersonMapper personMapper;
    @Resource private PersonIdentityWriteService identityWriter;
    @Resource private BusinessEventMapper eventMapper;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void complete(SalesOrderDO order, Long explicitLeadId, Long userId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LeadDO lead = explicitLeadId == null ? null : leadMapper.selectByIdForUpdate(explicitLeadId, tenantId);
        if (explicitLeadId != null && (lead == null || !Objects.equals(lead.getTenantId(), tenantId)
                || !Objects.equals(lead.getPersonId(), order.getPersonId()))) throw exception(SALES_ORDER_PERMISSION_DENIED);
        var person = personMapper.selectByIdForUpdate(order.getPersonId(), tenantId);
        if (person == null || !Objects.equals(person.getTenantId(), tenantId)) throw exception(SALES_ORDER_REPURCHASE_CUSTOMER_INVALID);
        String mobile = StrUtil.trimToNull(order.getStudentMobile());
        String wechat = StrUtil.trimToNull(order.getStudentWechatId());
        if (mobile == null || wechat == null) throw exception(SALES_ORDER_CONTACT_REQUIRED);
        var changed = new ArrayList<String>();
        boolean personMobile = StrUtil.isBlank(person.getMobile());
        boolean personWechat = StrUtil.isBlank(person.getWechatId());
        boolean leadMobile = lead != null && StrUtil.isBlank(lead.getSubmittedMobile());
        boolean leadWechat = lead != null && StrUtil.isBlank(lead.getSubmittedWechatId());
        // A completion must not introduce conflicting identities between the two live records.
        if (leadMobile && !personMobile && !mobile.equals(StrUtil.trim(person.getMobile()))
                || leadWechat && !personWechat && !wechat.equals(StrUtil.trim(person.getWechatId()))
                || personMobile && lead != null && !leadMobile && !mobile.equals(StrUtil.trim(lead.getSubmittedMobile()))
                || personWechat && lead != null && !leadWechat && !wechat.equals(StrUtil.trim(lead.getSubmittedWechatId()))) {
            throw exception(SALES_ORDER_REPURCHASE_IDENTITY_CONFLICT);
        }
        if (personMobile || personWechat) {
            identityWriter.update(person.getId(), person.getName(), personMobile ? mobile : person.getMobile(),
                    personWechat ? wechat : person.getWechatId());
            if (personMobile) changed.add("person.mobile");
            if (personWechat) changed.add("person.wechatId");
        }
        if (leadMobile || leadWechat) {
            if (leadMobile) { lead.setSubmittedMobile(mobile); changed.add("lead.submittedMobile"); }
            if (leadWechat) { lead.setSubmittedWechatId(wechat); changed.add("lead.submittedWechatId"); }
            leadMapper.updateById(lead);
        }
        if (changed.isEmpty()) return;
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType("sales_order_contacts_completed"); event.setAggregateType("person");
        event.setAggregateId(person.getId()); event.setOperatorUserId(userId);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("orderId", order.getId(), "changedFields", changed)));
        event.setOccurredAt(LocalDateTime.now()); event.setIdempotencyKey("order-contact-completion:" + order.getId());
        eventMapper.insert(event);
    }
}
