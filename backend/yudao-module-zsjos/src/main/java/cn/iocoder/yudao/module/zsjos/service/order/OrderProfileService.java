package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class OrderProfileService {
    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private RepurchaseCustomerService customers;
    @Resource private PersonIdentityWriteService identityWriter;
    @Resource private BusinessEventMapper eventMapper;

    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "enter-deal")
    public OrderProfileRespVO forLead(Long leadId) {
        var lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(SALES_ORDER_PERMISSION_DENIED);
        return snapshot(requirePerson(lead.getPersonId()));
    }

    @ZsjosPermission(bizType = "student", bizId = "#personId", action = "repurchase")
    public OrderProfileRespVO forStudent(Long personId) { return snapshot(requirePerson(personId)); }

    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "revise")
    public OrderProfileRespVO forOrder(Long orderId) {
        var order = orderMapper.selectById(orderId);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        return snapshot(requirePerson(order.getPersonId()));
    }

    // Exact identity verification is the existing narrow grant for the customer entry.
    public OrderProfileRespVO forCustomer(Long userId, RepurchaseCustomerCheckReqVO req) {
        var checked = customers.checkCustomer(userId, req);
        if (!checked.isCanRepurchase()) throw exception(SALES_ORDER_REPURCHASE_IDENTITY_CONFLICT);
        if (checked.getPersonId() == null) {
            var result = new OrderProfileRespVO(); result.setFingerprint("new"); return result;
        }
        var result = snapshot(requirePerson(checked.getPersonId()));
        // Identity matching grants purchase access, not disclosure of other current contact values.
        if (StrUtil.isNotBlank(result.getMobile()) && !Objects.equals(result.getMobile(), StrUtil.trim(req.getCustomerMobile())))
            result.setMobile("已登记（已隐藏）");
        if (StrUtil.isNotBlank(result.getWechatId()) && !Objects.equals(result.getWechatId(), StrUtil.trim(req.getCustomerWechatId())))
            result.setWechatId("已登记（已隐藏）");
        return result;
    }

    private PersonDO requirePerson(Long id) {
        var p = personMapper.selectById(id);
        if (p == null || !Objects.equals(p.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        return p;
    }

    public static OrderProfileRespVO snapshot(PersonDO p) {
        var result = new OrderProfileRespVO();
        result.setPersonId(p.getId()); result.setName(p.getName()); result.setMobile(p.getMobile()); result.setWechatId(p.getWechatId());
        result.setFingerprint(DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(
                p.getTenantId(), p.getId(), p.getName(), p.getMobile(), p.getWechatId()))));
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void apply(SalesOrderDO order, OrderProfileUpdateReqVO choice, Long userId) {
        var p = personMapper.selectByIdForUpdate(order.getPersonId(), TenantContextHolder.getRequiredTenantId());
        if (p == null || !Objects.equals(p.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        if ("new".equals(choice.getFingerprint()) && choice.getFields().isEmpty()
                && Objects.equals(StrUtil.trimToNull(p.getName()), StrUtil.trimToNull(order.getStudentName()))
                && Objects.equals(StrUtil.trimToNull(p.getMobile()), StrUtil.trimToNull(order.getStudentMobile()))
                && Objects.equals(StrUtil.trimToNull(p.getWechatId()), StrUtil.trimToNull(order.getStudentWechatId()))) return;
        if (!Objects.equals(snapshot(p).getFingerprint(), choice.getFingerprint())) throw exception(SALES_ORDER_PROFILE_CHANGED);
        var fields = choice.getFields();
        String name = fields.contains("name") || StrUtil.isBlank(p.getName()) ? StrUtil.trim(order.getStudentName()) : p.getName();
        String mobile = fields.contains("mobile") || StrUtil.isBlank(p.getMobile()) ? StrUtil.trim(order.getStudentMobile()) : p.getMobile();
        String wechat = fields.contains("wechatId") || StrUtil.isBlank(p.getWechatId()) ? StrUtil.trim(order.getStudentWechatId()) : p.getWechatId();
        var changed = new ArrayList<String>();
        if (!Objects.equals(name,p.getName())) changed.add("name");
        if (!Objects.equals(mobile,p.getMobile())) changed.add("mobile");
        if (!Objects.equals(wechat,p.getWechatId())) changed.add("wechatId");
        if (changed.isEmpty()) return;
        // Preserve the established unique-contact reservation and transaction boundary.
        String privateChange = EncryptTypeHandler.encrypt(JsonUtils.toJsonString(Map.of(
                "before", Arrays.asList(p.getName(),p.getMobile(),p.getWechatId()),
                "after", Arrays.asList(name,mobile,wechat))));
        identityWriter.update(p.getId(), name, mobile, wechat);
        var event = new BusinessEventDO(); event.setEventType("sales_order_profile_updated");
        event.setAggregateType("person"); event.setAggregateId(p.getId()); event.setOperatorUserId(userId);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("orderId",order.getId(),"changedFields",changed,
                "encryptedIdentityChange",privateChange)));
        event.setOccurredAt(LocalDateTime.now()); event.setIdempotencyKey("order-profile-update:"+order.getId());
        eventMapper.insert(event);
    }
}
