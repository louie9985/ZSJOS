package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
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

    @Test
    void registersAllScenesWithSceneSpecificVariables() {
        List<NotifySceneRespDTO> scenes = provider.getScenes();

        assertEquals(20, scenes.size());
        assertTrue(variableKeys(scene(scenes, ASSIGNED)).contains("assignment.attempt"));
        assertFalse(variableKeys(scene(scenes, ASSIGNED)).contains("followUp.result"));
        assertTrue(variableKeys(scene(scenes, FOLLOW_UP_RECORDED)).contains("followUp.result"));
        assertFalse(variableKeys(scene(scenes, FOLLOW_UP_RECORDED)).contains("assignment.attempt"));
        assertTrue(variableKeys(scene(scenes, APPEAL_SUBMITTED)).contains("appeal.roundNo"));
    }

    @Test
    void resolvesAppealReviewersFromEventPayload() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(APPEAL_SUBMITTED)
                .payload(Map.of("appeal.reviewerUserIds", List.of(31L, 32L))).build();
        assertEquals(Set.of(31L, 32L), provider.resolveRecipients(event, Set.of(ROLE_APPEAL_REVIEWERS)));
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

        Set<Long> recipients = provider.resolveRecipients(event, Set.of(ROLE_OWNER, ROLE_QUALIFICATION_MANAGERS));

        assertEquals(Set.of(20L, 21L, 22L), recipients);
    }

    @Test
    void resolvesContactValuesAccordingToRecipientPermission() {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setSourceUserId(10L);
        lead.setOwnerUserId(20L);
        lead.setSubmittedName("张三丰");
        lead.setSubmittedMobile("13800138000");
        lead.setSubmittedWechatId("wechat-full");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(productMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode(ASSIGNED).bizId(1L).build();
        Map<String, Object> masked = provider.resolveVariables(event, 30L);
        Map<String, Object> full = provider.resolveVariables(event, 10L);

        assertFalse("13800138000".equals(masked.get("lead.mobile")));
        assertFalse("wechat-full".equals(masked.get("lead.wechatId")));
        assertEquals("13800138000", full.get("lead.mobile"));
        assertEquals("wechat-full", full.get("lead.wechatId"));
    }

    private static NotifySceneRespDTO scene(List<NotifySceneRespDTO> scenes, String code) {
        return scenes.stream().filter(item -> code.equals(item.getCode())).findFirst().orElseThrow();
    }

    private static List<String> variableKeys(NotifySceneRespDTO scene) {
        return scene.getVariables().stream().map(NotifySceneVariableRespDTO::getKey).toList();
    }
}
