package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.PersonnelStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_DISABLED;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_SUBMITTER_IDENTITY_INVALID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LeadSubmissionIdentityServiceTest {

    @InjectMocks private LeadSubmissionIdentityService service;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PersonnelStateService personnelStateService;

    private void allowEnabledPersonnel(Long userId) {
        lenient().when(personnelStateService.isEnabled(userId)).thenReturn(true);
    }

    @Test
    void ordinarySubmissionAllowsEnabledInternalUserWithoutPost() {
        allowEnabledPersonnel(1L);
        AdminUserRespDTO user = user(1L, 10L, Set.of());
        when(adminUserApi.getUser(1L)).thenReturn(user);
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(10L); dept.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(deptApi.getDept(10L)).thenReturn(dept);

        assertEquals(LeadSubmissionIdentityService.Identity.NEW_MEDIA,
                service.requireOrdinarySubmitter(1L).identity());
    }

    @Test
    void historicalNewMediaRightsSurvivePostChangeWhileAccountEnabled() {
        allowEnabledPersonnel(4L);
        when(adminUserApi.getUser(4L)).thenReturn(user(4L, 30L, Set.of()));

        assertDoesNotThrow(() -> service.resolveHistoricalSubmission(4L, SOURCE_INTERNAL_NEW_MEDIA, null));
    }

    @Test
    void historicalPartnerRightsRequireSamePartnerSubjectWhileAccountEnabled() {
        allowEnabledPersonnel(5L);
        when(adminUserApi.getUser(5L)).thenReturn(user(5L, null, Set.of()));
        PartnerDO partner = new PartnerDO(); partner.setId(50L); partner.setBoundSystemUserId(5L);
        partner.setStatus(PARTNER_STATUS_ENABLED); partner.setEnabledAt(LocalDateTime.now());
        when(partnerMapper.selectById(50L)).thenReturn(partner);
        assertDoesNotThrow(() -> service.resolveHistoricalSubmission(5L, SOURCE_PARTNER, 50L));

        partner.setStatus(PARTNER_STATUS_DISABLED);
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveHistoricalSubmission(5L, SOURCE_PARTNER, 50L));
        assertEquals(LEAD_SUBMITTER_IDENTITY_INVALID.getCode(), error.getCode());
    }

    @Test
    void historicalSubmitterRejectsDifferentUser() {
        LeadDO lead = new LeadDO(); lead.setSourceUserId(6L); lead.setSourceType(SOURCE_INTERNAL_NEW_MEDIA);
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.requireHistoricalSubmitter(lead, 7L));
        assertEquals(LEAD_SUBMITTER_IDENTITY_INVALID.getCode(), error.getCode());
    }

    private AdminUserRespDTO user(Long id, Long deptId, Set<Long> postIds) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setDeptId(deptId);
        user.setPostIds(postIds); user.setStatus(CommonStatusEnum.ENABLE.getStatus()); return user;
    }
}
