package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Component
public class LeadSubmitterFeedbackPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private LeadMapper leadMapper;
    @Resource private PartnerAccountService partnerAccountService;

    @Override public String getBizType() { return "lead-submitter-feedback"; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        try { check(id, action, userId); return true; }
        catch (ServiceException ex) { return false; }
    }
    @Override public void check(Long id, String action, Long userId) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        boolean allowed = false;
        if (getLoginUser() != null && UserTypeEnum.PARTNER.getValue().equals(getLoginUser().getUserType())) {
            Long partnerId = partnerAccountService.requireContext(userId).partnerId();
            allowed = "read-partner".equals(action) && PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType())
                    && Objects.equals(partnerId, lead.getProviderOwnerId());
        } else if (getLoginUser() != null && UserTypeEnum.ADMIN.getValue().equals(getLoginUser().getUserType())) {
            allowed = switch (action) {
                case "read" -> canRead(lead, userId);
                case "create" -> userId != null && Objects.equals(userId, lead.getOwnerUserId());
                default -> false;
            };
        }
        if (!allowed) throw exception(LEAD_PERMISSION_DENIED);
    }
    public boolean canRead(LeadDO lead, Long userId) {
        return userId != null && (Objects.equals(userId, lead.getOwnerUserId())
                || PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                && Objects.equals(userId, lead.getProviderOwnerId()));
    }
    public boolean canCreate(LeadDO lead, Long userId) {
        return userId != null && Objects.equals(userId, lead.getOwnerUserId())
                && lead.getStatus() != null && !Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus())
                && lead.getProviderOwnerId() != null;
    }
}
