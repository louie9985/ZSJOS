package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadNotifySceneProviderTest {

    @InjectMocks
    private LeadNotifySceneProvider provider;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadIntendedProductMapper productMapper;
    @Mock private LeadAttachmentMapper attachmentMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PermissionApi permissionApi;
    @Mock private DeptApi deptApi;
    @Mock private LeadAgingPoolCycleMapper agingPoolCycleMapper;
    @Mock private LeadAssignmentService assignmentService;

    @Test
    void registersAllScenesWithSceneSpecificVariables() {
        List<NotifySceneRespDTO> scenes = provider.getScenes();

        assertEquals(39, scenes.size());
        assertEquals(39, scenes.stream().map(NotifySceneRespDTO::getCode).distinct().count());
        assertTrue(variableKeys(scene(scenes, ASSIGNED)).contains("lead.no"));
        assertTrue(variableKeys(scene(scenes, ASSIGNED)).contains("assignment.attempt"));
        assertFalse(variableKeys(scene(scenes, ASSIGNED)).contains("followUp.result"));
        assertTrue(variableKeys(scene(scenes, FOLLOW_UP_RECORDED)).contains("followUp.result"));
        assertFalse(variableKeys(scene(scenes, FOLLOW_UP_RECORDED)).contains("assignment.attempt"));
        assertTrue(variableKeys(scene(scenes, APPEAL_SUBMITTED)).contains("appeal.roundNo"));
        assertTrue(variableKeys(scene(scenes, SUBMITTER_URGED)).contains("urge.reason"));
        assertTrue(scenes.stream().anyMatch(item -> COMPLAINT_FOUNDED.equals(item.getCode())));
    }

    @Test
    void resolvesAppealReviewersFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(APPEAL_SUBMITTED)
                .payload(Map.of("appeal.reviewerUserIds", List.of(31L, 32L))).build();
        assertEquals(Set.of(NotifyRecipientDTO.admin(31L), NotifyRecipientDTO.admin(32L)),
                provider.resolveRecipients(event, Set.of(ROLE_APPEAL_REVIEWERS)));
    }

    @Test
    void resolvesLeadCreatedProviderAndActualOperator() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(CREATED)
                .operatorUserId(10L).payload(Map.of("submitterUserId", 20L)).build();

        assertEquals(Set.of(NotifyRecipientDTO.admin(10L), NotifyRecipientDTO.admin(20L)),
                provider.resolveRecipients(event, Set.of(ROLE_SUBMITTER, ROLE_OPERATOR)));
    }

    @Test
    void resolvesQualificationManagersFromOwnerDepartmentHierarchy() {
        AdminUserRespDTO owner = new AdminUserRespDTO();
        owner.setId(20L); owner.setDeptId(200L);
        DeptRespDTO child = new DeptRespDTO();
        child.setId(200L); child.setParentId(100L); child.setLeaderUserId(21L);
        DeptRespDTO parent = new DeptRespDTO();
        parent.setId(100L); parent.setLeaderUserId(22L);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        when(deptApi.getDept(200L)).thenReturn(child);
        when(deptApi.getDept(100L)).thenReturn(parent);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(QUALIFICATION_SUSPENDED)
                .payload(Map.of("ownerUserId", 20L)).build();

        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event, Set.of(ROLE_OWNER, ROLE_QUALIFICATION_MANAGERS));

        assertEquals(Set.of(NotifyRecipientDTO.admin(20L), NotifyRecipientDTO.admin(21L),
                NotifyRecipientDTO.admin(22L)), recipients);
    }

    @Test
    void resolvesContactValuesAccordingToRecipientPermission() {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setLeadNo("KZ202608160000000001");
        lead.setSourceUserId(10L);
        lead.setOwnerUserId(20L);
        lead.setSubmittedName("张三丰");
        lead.setSubmittedMobile("13800138000");
        lead.setSubmittedWechatId("wechat-full");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build();
        Map<String, Object> masked = provider.resolveVariables(event, NotifyRecipientDTO.admin(30L));
        Map<String, Object> full = provider.resolveVariables(event, NotifyRecipientDTO.admin(10L));

        assertEquals("KZ202608160000000001", full.get("lead.no"));
        assertEquals(1L, full.get("lead.id"));
        assertFalse("13800138000".equals(masked.get("lead.mobile")));
        assertFalse("wechat-full".equals(masked.get("lead.wechatId")));
        assertEquals("13800138000", full.get("lead.mobile"));
        assertEquals("wechat-full", full.get("lead.wechatId"));
    }

    @Test
    void blindsCounterpartIdentityForSubmitterAndOwnerNotifications() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setSourceUserId(10L); lead.setOwnerUserId(20L);
        lead.setAssignmentStatus("owned"); lead.setSubmittedName("张三丰");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        AdminUserRespDTO submitter = new AdminUserRespDTO(); submitter.setId(10L); submitter.setNickname("提交销售");
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(20L); owner.setNickname("负责销售");
        when(adminUserApi.getUser(10L)).thenReturn(submitter);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build();

        Map<String, Object> submitterValues = provider.resolveVariables(event, NotifyRecipientDTO.admin(10L));
        Map<String, Object> ownerValues = provider.resolveVariables(event, NotifyRecipientDTO.admin(20L));

        assertNotEquals("负责销售", submitterValues.get("owner.name"));
        assertEquals(null, submitterValues.get("owner.id"));
        assertNotEquals("提交销售", ownerValues.get("submitter.name"));
        assertEquals(null, ownerValues.get("submitter.id"));
    }

    @Test
    void keepsCounterpartIdentityForQueryAllNotificationRecipient() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setSourceUserId(10L); lead.setOwnerUserId(20L); lead.setAssignmentStatus("owned");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        AdminUserRespDTO submitter = new AdminUserRespDTO(); submitter.setId(10L); submitter.setNickname("提交销售");
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(20L); owner.setNickname("负责销售");
        when(adminUserApi.getUser(10L)).thenReturn(submitter);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        when(permissionApi.hasAnyPermissions(30L, "zsjos:lead:query-all")).thenReturn(true);

        Map<String, Object> values = provider.resolveVariables(
                NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build(), NotifyRecipientDTO.admin(30L));

        assertEquals("提交销售", values.get("submitter.name"));
        assertEquals("负责销售", values.get("owner.name"));
        assertEquals(10L, values.get("submitter.id"));
    }

    private static NotifySceneRespDTO scene(List<NotifySceneRespDTO> scenes, String code) {
        return scenes.stream().filter(item -> code.equals(item.getCode())).findFirst().orElseThrow();
    }

    private static List<String> variableKeys(NotifySceneRespDTO scene) {
        return scene.getVariables().stream().map(NotifySceneVariableRespDTO::getKey).toList();
    }
}
