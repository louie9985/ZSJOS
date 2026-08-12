package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_SUBMITTER_IDENTITY_INVALID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadSubmissionIdentityServiceTest {

    @InjectMocks private LeadSubmissionIdentityService service;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PostApi postApi;
    @Mock private DeptApi deptApi;
    @Mock private PartnerMapper partnerMapper;

    @Test
    void ordinarySubmissionAllowsNewMediaOperator() {
        AdminUserRespDTO user = user(1L, 10L, Set.of(100L));
        when(adminUserApi.getUser(1L)).thenReturn(user);
        when(postApi.getPostByCode("new_media_operator")).thenReturn(post(100L));

        assertEquals(LeadSubmissionIdentityService.Identity.NEW_MEDIA,
                service.requireOrdinarySubmitter(1L).identity());
    }

    @Test
    void ordinarySubmissionAllowsLeaderWithManagerPostAndNewMediaStaff() {
        AdminUserRespDTO manager = user(2L, 20L, Set.of(200L));
        AdminUserRespDTO operator = user(3L, 20L, Set.of(100L));
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(20L); dept.setLeaderUserId(2L);
        when(adminUserApi.getUser(2L)).thenReturn(manager);
        when(postApi.getPostByCode("new_media_operator")).thenReturn(post(100L));
        when(postApi.getPostByCode("dept_manager")).thenReturn(post(200L));
        when(deptApi.getDeptListByLeaderUserId(2L)).thenReturn(List.of(dept));
        when(adminUserApi.getUserListByDeptIds(List.of(20L))).thenReturn(List.of(manager, operator));

        assertEquals(LeadSubmissionIdentityService.Identity.NEW_MEDIA_MANAGER,
                service.requireOrdinarySubmitter(2L).identity());
    }

    @Test
    void historicalNewMediaRightsSurvivePostChangeWhileAccountEnabled() {
        when(adminUserApi.getUser(4L)).thenReturn(user(4L, 30L, Set.of()));

        assertDoesNotThrow(() -> service.resolveHistoricalSubmission(4L, SOURCE_INTERNAL_NEW_MEDIA, null));
    }

    @Test
    void historicalPartnerRightsRequireSameEnabledPartnerSubject() {
        when(adminUserApi.getUser(5L)).thenReturn(user(5L, null, Set.of()));
        PartnerDO partner = new PartnerDO(); partner.setId(50L); partner.setBoundSystemUserId(5L);
        partner.setEnabledAt(LocalDateTime.now());
        when(partnerMapper.selectById(50L)).thenReturn(partner);
        assertDoesNotThrow(() -> service.resolveHistoricalSubmission(5L, SOURCE_PARTNER, 50L));

        partner.setDisabledAt(LocalDateTime.now());
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

    private PostRespDTO post(Long id) {
        PostRespDTO post = new PostRespDTO(); post.setId(id); post.setStatus(CommonStatusEnum.ENABLE.getStatus()); return post;
    }
}
