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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadProviderAttributionServiceTest {

    @InjectMocks private LeadProviderAttributionService service;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PartnerOwnershipService partnerOwnershipService;

    private void stubOrganization() {
        when(adminUserApi.getUser(10L)).thenReturn(user(10L, 100L, "成员甲"));
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 100L, "主管甲"));
        when(deptApi.getDept(100L)).thenReturn(new DeptRespDTO().setId(100L).setName("新媒体一部")
                .setLeaderUserId(20L));
    }

    @Test
    void freezesNewMediaOperatorAndManagerOrganizationSnapshots() {
        stubOrganization();
        for (LeadSubmissionIdentityService.Identity identity : new LeadSubmissionIdentityService.Identity[]{
                LeadSubmissionIdentityService.Identity.NEW_MEDIA,
                LeadSubmissionIdentityService.Identity.NEW_MEDIA_MANAGER}) {
            LeadDO lead = new LeadDO().setSourceUserId(10L);
            LocalDateTime countedAt = LocalDateTime.of(2026, 8, 27, 9, 0);

            service.apply(lead, identity, null, countedAt);

            assertSystemAttribution(lead, 10L, "成员甲", countedAt);
        }
    }

    @Test
    void salesWithoutProviderHasNoProviderAttributionButStillFreezesCountedAt() {
        LeadDO lead = new LeadDO().setSourceUserId(30L);
        LocalDateTime countedAt = LocalDateTime.of(2026, 8, 27, 9, 10);

        service.apply(lead, LeadSubmissionIdentityService.Identity.SALES, null, countedAt);

        assertEquals(countedAt, lead.getCountedAt());
        assertNull(lead.getProviderOwnerType());
        assertNull(lead.getProviderOwnerId());
        assertNull(lead.getContributionUserIdSnapshot());
    }

    @Test
    void salesWithSelectedProviderFreezesSelectedSystemUser() {
        stubOrganization();
        LeadDO lead = new LeadDO().setSourceUserId(30L);
        LocalDateTime countedAt = LocalDateTime.of(2026, 8, 27, 9, 20);

        service.apply(lead, LeadSubmissionIdentityService.Identity.SALES, 10L, countedAt);

        assertSystemAttribution(lead, 10L, "成员甲", countedAt);
    }

    @Test
    void partnerFreezesPartnerAndCurrentEmployeeOwnershipSnapshots() {
        stubOrganization();
        when(partnerMapper.selectById(80L)).thenReturn(new PartnerDO().setId(80L).setName("合作方甲"));
        PartnerOwnershipDO ownership = new PartnerOwnershipDO();
        ownership.setPartnerId(80L);
        ownership.setEmployeeUserId(10L);
        ownership.setEmployeeNameSnapshot("归属成员甲");
        when(partnerOwnershipService.getByPartnerId(80L)).thenReturn(ownership);
        LeadDO lead = new LeadDO().setPartnerId(80L);
        LocalDateTime countedAt = LocalDateTime.of(2026, 8, 27, 9, 30);

        service.apply(lead, LeadSubmissionIdentityService.Identity.PARTNER, null, countedAt);

        assertEquals("partner", lead.getProviderOwnerType());
        assertEquals(80L, lead.getProviderOwnerId());
        assertEquals("合作方甲", lead.getProviderOwnerNameSnapshot());
        assertEquals(10L, lead.getContributionUserIdSnapshot());
        assertEquals("归属成员甲", lead.getContributionUserNameSnapshot());
        assertEquals(countedAt, lead.getCountedAt());
    }

    @Test
    void partnerWithoutEmployeeOwnershipDoesNotInventContributionSnapshot() {
        when(partnerMapper.selectById(81L)).thenReturn(new PartnerDO().setId(81L).setName("合作方乙"));
        LeadDO lead = new LeadDO().setPartnerId(81L);

        service.apply(lead, LeadSubmissionIdentityService.Identity.PARTNER, null,
                LocalDateTime.of(2026, 8, 27, 9, 40));

        assertEquals("partner", lead.getProviderOwnerType());
        assertEquals(81L, lead.getProviderOwnerId());
        assertNull(lead.getContributionUserIdSnapshot());
    }

    private void assertSystemAttribution(LeadDO lead, Long userId, String userName, LocalDateTime countedAt) {
        assertEquals("system_user", lead.getProviderOwnerType());
        assertEquals(userId, lead.getProviderOwnerId());
        assertEquals(userName, lead.getProviderOwnerNameSnapshot());
        assertEquals(userId, lead.getContributionUserIdSnapshot());
        assertEquals(100L, lead.getContributionDeptIdSnapshot());
        assertEquals("新媒体一部", lead.getContributionDeptNameSnapshot());
        assertEquals(20L, lead.getContributionSupervisorUserIdSnapshot());
        assertEquals("主管甲", lead.getContributionSupervisorNameSnapshot());
        assertEquals(countedAt, lead.getCountedAt());
    }

    private static AdminUserRespDTO user(Long id, Long deptId, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setDeptId(deptId);
        user.setNickname(nickname);
        return user;
    }
}
