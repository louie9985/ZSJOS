package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionIdentityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ACTIVE_ORDER_STATUSES;

/** Exact identity grants only a purchase operation, never a customer-list or Lead read grant. */
@Service
public class RepurchaseCustomerService implements ZsjosObjectPermissionProvider {
    @Resource private PersonMapper personMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private LeadSubmissionIdentityService identityService;

    public void requireActor(Long userId) {
        if (!permissionApi.hasAnyPermissions(userId, "zsjos:sales-order:create")) throw exception(SALES_ORDER_PERMISSION_DENIED);
        identityService.requireEducationSubmitter(userId); // Existing enabled internal-personnel contract.
    }

    public RepurchaseCustomerCheckRespVO checkCustomer(Long userId, RepurchaseCustomerCheckReqVO req) {
        requireActor(userId);
        String name = StrUtil.trimToNull(req.getCustomerName());
        String mobile = StrUtil.trimToNull(req.getCustomerMobile());
        String wechat = StrUtil.trimToNull(req.getCustomerWechatId());
        if (name == null || mobile == null && wechat == null) return result("IDENTITY_CONFLICT", false, "请填写姓名及手机号或微信号");
        List<PersonDO> candidates = personMapper.selectDuplicateCandidates(mobile, wechat).stream()
                .filter(p -> Objects.equals(p.getTenantId(), TenantContextHolder.getRequiredTenantId())).toList();
        if (candidates.size() > 1) return result("MULTIPLE_MATCH", false, "联系方式对应多个客户，请联系管理员核实");
        if (candidates.isEmpty()) return result("NO_MATCH", true, "未找到系统客户，将按系统外历史客户录入复购");
        PersonDO person = candidates.getFirst();
        if (!matches(person, name, mobile, wechat)) return result("IDENTITY_CONFLICT", false, "姓名或联系方式与已有客户不一致，请核实后重试");
        if (orderMapper.selectActiveRepurchaseByPersonId(person.getId(), ACTIVE_ORDER_STATUSES) != null)
            return result("REPURCHASE_BLOCKED", false, "该客户已有待处理复购订单，请先处理原订单");
        RepurchaseCustomerCheckRespVO response = result("EXISTING_CUSTOMER", true, "已识别客户，本次订单归属当前录单人，原客资及历史订单不变");
        response.setPersonId(person.getId()); response.setCustomerName(person.getName());
        String stored = person.getMobile();
        response.setMaskedMobile(stored == null ? null : stored.length() > 7 ? stored.substring(0,3) + "****" + stored.substring(stored.length()-4) : "****");
        return response;
    }

    public PersonDO requireIdentity(Long personId, SalesOrderRepurchaseReqVO req) {
        PersonDO person = personMapper.selectByIdForUpdate(personId, TenantContextHolder.getRequiredTenantId());
        if (person == null || !Objects.equals(person.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !matches(person, StrUtil.trimToNull(req.getCustomerName()), StrUtil.trimToNull(req.getCustomerMobile()), StrUtil.trimToNull(req.getCustomerWechatId())))
            throw exception(SALES_ORDER_REPURCHASE_IDENTITY_CONFLICT);
        return person;
    }
    private boolean matches(PersonDO p, String name, String mobile, String wechat) {
        return name != null && name.equals(StrUtil.trim(p.getName())) && (mobile != null || wechat != null)
                && (mobile == null || mobile.equals(StrUtil.trim(p.getMobile())))
                && (wechat == null || wechat.equalsIgnoreCase(StrUtil.trim(p.getWechatId())));
    }
    private RepurchaseCustomerCheckRespVO result(String status, boolean allowed, String reason) {
        RepurchaseCustomerCheckRespVO r = new RepurchaseCustomerCheckRespVO();
        r.setMatchStatus(status); r.setCanRepurchase(allowed); r.setReason(reason); return r;
    }
    @Override public String getBizType() { return "repurchase-customer"; }
    @Override public boolean hasPermission(Long personId, String action, Long userId) {
        if (!"create".equals(action) || !permissionApi.hasAnyPermissions(userId, "zsjos:sales-order:create")) return false;
        PersonDO p = personMapper.selectById(personId);
        return p != null && Objects.equals(p.getTenantId(), TenantContextHolder.getRequiredTenantId());
    }
    @Override public void check(Long personId, String action, Long userId) {
        if (!hasPermission(personId, action, userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
    }
}
