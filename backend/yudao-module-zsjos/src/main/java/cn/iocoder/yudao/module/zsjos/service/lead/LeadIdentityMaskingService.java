package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DISPATCH_SPECIFIED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_PARTNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_SYSTEM_USER;

/**
 * Lead 详情域的关系型身份展示策略。授权判断仍由 LeadObjectPermissionService 负责，
 * 本服务只把该判断和 Lead 双方关系转换为稳定的展示名称，避免各投影自行决定是否脱敏。
 */
@Service
@RequiredArgsConstructor
public class LeadIdentityMaskingService {

    private final LeadObjectPermissionService permissionService;

    public LeadIdentityViewContext resolve(Long viewerId, LeadDO lead) {
        boolean canViewUnmasked = permissionService.canViewUnmaskedIdentity(viewerId, lead);
        SourceIdentityType sourceIdentityType = sourceIdentityType(lead);
        Long sourceEmployeeUserId = sourceIdentityType == SourceIdentityType.EMPLOYEE
                ? lead.getProviderOwnerId() : null;
        Long sourcePartnerId = sourceIdentityType == SourceIdentityType.PARTNER
                ? lead.getProviderOwnerId() : null;
        boolean viewerIsSubmitter = Objects.equals(viewerId, sourceEmployeeUserId);
        boolean viewerIsOwner = Objects.equals(viewerId, lead.getOwnerUserId());
        boolean sourceDiffersFromOwner = sourceIdentityType == SourceIdentityType.PARTNER
                ? sourcePartnerId != null
                : sourceEmployeeUserId != null && !Objects.equals(sourceEmployeeUserId, lead.getOwnerUserId());
        // Partner business names stay masked for the sales owner even after specified dispatch.
        boolean counterpartyMasking = (!DISPATCH_SPECIFIED.equals(lead.getDispatchMode())
                || sourceIdentityType == SourceIdentityType.PARTNER)
                && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                && sourceDiffersFromOwner && lead.getOwnerUserId() != null
                && !canViewUnmasked;
        return new LeadIdentityViewContext(viewerId, sourceIdentityType, sourceEmployeeUserId,
                sourcePartnerId, lead.getOwnerUserId(), viewerIsSubmitter, viewerIsOwner,
                canViewUnmasked, counterpartyMasking);
    }

    public String employeeName(LeadIdentityViewContext context, Map<Long, AdminUserRespDTO> users,
                               Long userId, LeadIdentityRole role) {
        if (userId == null) return null;
        if (userId == 0) return "系统";
        AdminUserRespDTO user = users == null ? null : users.get(userId);
        if (user == null) return "未知账号";
        String name = user.getNickname();
        if (name == null || name.isBlank()) return "未知账号";
        return shouldMask(context, userId, role) ? maskName(name) : name;
    }

    public String partnerName(LeadIdentityViewContext context, String name) {
        if (name == null || name.isBlank()) return name;
        return context.sourceIdentityType() == SourceIdentityType.PARTNER
                && context.counterpartyMaskingEnabled() && context.viewerIsOwner()
                ? maskName(name) : name;
    }

    /** Projects a persisted identity snapshot without re-resolving or changing the snapshot. */
    public String snapshotName(LeadIdentityViewContext context, String name, Long userId,
                               LeadIdentityRole role) {
        if (name == null || name.isBlank()) return name;
        return shouldMask(context, userId, role) ? maskName(name) : name;
    }

    public boolean isMasked(LeadIdentityViewContext context, Long userId) {
        return shouldMask(context, userId, LeadIdentityRole.OPERATOR);
    }

    private boolean shouldMask(LeadIdentityViewContext context, Long userId, LeadIdentityRole role) {
        if (!context.counterpartyMaskingEnabled() || userId == null || userId == 0) return false;
        boolean sourceCounterparty = context.viewerIsOwner()
                && context.sourceIdentityType() == SourceIdentityType.EMPLOYEE
                && Objects.equals(userId, context.sourceEmployeeUserId());
        boolean ownerCounterparty = context.viewerIsSubmitter()
                && Objects.equals(userId, context.ownerUserId());
        return sourceCounterparty || ownerCounterparty;
    }

    private static String maskName(String name) {
        return name == null ? null : DesensitizedUtil.chineseName(name);
    }

    private static SourceIdentityType sourceIdentityType(LeadDO lead) {
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())) return SourceIdentityType.EMPLOYEE;
        if (PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType())) return SourceIdentityType.PARTNER;
        return SourceIdentityType.NONE;
    }

    public enum SourceIdentityType {
        EMPLOYEE,
        PARTNER,
        NONE
    }

    public record LeadIdentityViewContext(Long viewerId, SourceIdentityType sourceIdentityType,
                                          Long sourceEmployeeUserId, Long sourcePartnerId, Long ownerUserId,
                                          boolean viewerIsSubmitter, boolean viewerIsOwner,
                                          boolean canViewUnmaskedIdentity,
                                          boolean counterpartyMaskingEnabled) {
    }
}
