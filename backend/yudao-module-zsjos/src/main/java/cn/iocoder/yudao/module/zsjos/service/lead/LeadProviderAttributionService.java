package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_PARTNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_SYSTEM_USER;

/** Freezes provider and organization facts once, when the Lead first becomes countable. */
@Service
public class LeadProviderAttributionService {

    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PartnerMapper partnerMapper;
    @Resource private PartnerOwnershipService partnerOwnershipService;

    public void apply(LeadDO lead, LeadSubmissionIdentityService.Identity identity,
                      Long selectedProviderUserId, LocalDateTime countedAt) {
        lead.setCountedAt(countedAt);
        switch (identity) {
            case NEW_MEDIA -> applySystemUser(lead, lead.getSourceUserId());
            case SALES -> {
                if (selectedProviderUserId != null) applySystemUser(lead, selectedProviderUserId);
            }
            case PARTNER -> applyPartner(lead, lead.getPartnerId());
        }
    }

    private void applySystemUser(LeadDO lead, Long userId) {
        if (userId == null) return;
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null) return;
        lead.setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER);
        lead.setProviderOwnerId(userId);
        lead.setProviderOwnerNameSnapshot(user.getNickname());
        applyContributionUser(lead, user, user.getNickname());
    }

    private void applyPartner(LeadDO lead, Long partnerId) {
        if (partnerId == null) return;
        PartnerDO partner = partnerMapper.selectById(partnerId);
        if (partner == null) return;
        lead.setProviderOwnerType(PROVIDER_OWNER_PARTNER);
        lead.setProviderOwnerId(partnerId);
        lead.setProviderOwnerNameSnapshot(partner.getName());
        PartnerOwnershipDO ownership = partnerOwnershipService.getByPartnerId(partnerId);
        if (ownership == null) return;
        AdminUserRespDTO employee = adminUserApi.getUser(ownership.getEmployeeUserId());
        if (employee == null) return;
        applyContributionUser(lead, employee, ownership.getEmployeeNameSnapshot());
    }

    private void applyContributionUser(LeadDO lead, AdminUserRespDTO user, String nameSnapshot) {
        lead.setContributionUserIdSnapshot(user.getId());
        lead.setContributionUserNameSnapshot(nameSnapshot);
        if (user.getDeptId() == null) return;
        DeptRespDTO dept = deptApi.getDept(user.getDeptId());
        if (dept == null) return;
        lead.setContributionDeptIdSnapshot(dept.getId());
        lead.setContributionDeptNameSnapshot(dept.getName());
        lead.setContributionSupervisorUserIdSnapshot(dept.getLeaderUserId());
        if (dept.getLeaderUserId() != null) {
            AdminUserRespDTO supervisor = adminUserApi.getUser(dept.getLeaderUserId());
            lead.setContributionSupervisorNameSnapshot(supervisor == null ? null : supervisor.getNickname());
        }
    }
}
