package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyRuleSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyRuleMapper;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class NotifyRuleServiceImplTest {

    @InjectMocks private NotifyRuleServiceImpl service;
    @Mock private NotifyRuleMapper notifyRuleMapper;
    @Mock private NotifyTemplateService notifyTemplateService;
    @Mock private NotifySceneRegistry sceneRegistry;
    @Mock private AdminUserService adminUserService;

    @Test
    void createValidatesAndDeduplicatesRecipients() {
        NotifyRuleSaveReqVO request = request();
        request.setRecipientRoles(List.of("owner", "owner"));
        request.setSpecifiedUserIds(List.of(10L, 10L, 20L));
        stubValidCatalog();

        service.createNotifyRule(request);

        ArgumentCaptor<NotifyRuleDO> captor = ArgumentCaptor.forClass(NotifyRuleDO.class);
        verify(notifyRuleMapper).insert(captor.capture());
        assertEquals(List.of("owner"), captor.getValue().getRecipientRoles());
        assertEquals(List.of(10L, 20L), captor.getValue().getSpecifiedUserIds());
        verify(adminUserService).validateUserList(List.of(10L, 10L, 20L));
    }

    @Test
    void createRejectsUnknownScene() {
        NotifyRuleSaveReqVO request = request();
        when(sceneRegistry.getScene("test.scene")).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.createNotifyRule(request));

        verify(notifyRuleMapper, never()).insert(any(NotifyRuleDO.class));
    }

    @Test
    void createRejectsSmsBusinessChannel() {
        NotifyRuleSaveReqVO request = request();
        request.setChannelCode("sms");

        assertThrows(ServiceException.class, () -> service.createNotifyRule(request));

        verify(notifyRuleMapper, never()).insert(any(NotifyRuleDO.class));
    }

    @Test
    void createRejectsUnknownTimingStage() {
        NotifyRuleSaveReqVO request = request();
        request.setTimingStage("later");
        request.setTimingOffsetMinutes(30);
        stubValidTimedCatalog();

        assertThrows(ServiceException.class, () -> service.createNotifyRule(request));

        verify(notifyRuleMapper, never()).insert(any(NotifyRuleDO.class));
    }

    @Test
    void createRejectsNonZeroDueOffset() {
        NotifyRuleSaveReqVO request = request();
        request.setTimingStage("due");
        request.setTimingOffsetMinutes(5);
        stubValidTimedCatalog();

        assertThrows(ServiceException.class, () -> service.createNotifyRule(request));

        verify(notifyRuleMapper, never()).insert(any(NotifyRuleDO.class));
    }

    @Test
    void createClearsTimingForNonTimedScene() {
        NotifyRuleSaveReqVO request = request();
        request.setTimingStage("advance");
        request.setTimingOffsetMinutes(30);
        stubValidCatalog();

        service.createNotifyRule(request);

        ArgumentCaptor<NotifyRuleDO> captor = ArgumentCaptor.forClass(NotifyRuleDO.class);
        verify(notifyRuleMapper).insert(captor.capture());
        assertNull(captor.getValue().getTimingStage());
        assertNull(captor.getValue().getTimingOffsetMinutes());
    }

    @Test
    void enablingExistingRuleRevalidatesTemplateScene() {
        NotifyRuleDO existing = NotifyRuleDO.builder().id(1L).name("rule").sceneCode("test.scene")
                .templateId(2L).recipientRoles(List.of("owner")).specifiedUserIds(List.of())
                .actionType(NotifyActionType.MESSAGE_DETAIL).status(CommonStatusEnum.DISABLE.getStatus()).build();
        when(notifyRuleMapper.selectById(1L)).thenReturn(existing);
        when(sceneRegistry.getScene("test.scene")).thenReturn(scene());
        when(notifyTemplateService.getNotifyTemplate(2L))
                .thenReturn(NotifyTemplateDO.builder().id(2L).sceneCode("other.scene").build());

        assertThrows(ServiceException.class,
                () -> service.updateNotifyRuleStatus(1L, CommonStatusEnum.ENABLE.getStatus()));

        verify(notifyRuleMapper, never()).updateById(any(NotifyRuleDO.class));
    }

    @Test
    void initializeDefaultRulesCreatesMissingRule() {
        NotifyDefaultRuleReqDTO seed = NotifyDefaultRuleReqDTO.builder().name("default rule")
                .sceneCode("test.scene").templateCode("TEST_TEMPLATE")
                .recipientRoles(List.of("owner")).actionType(NotifyActionType.MESSAGE_DETAIL).build();
        NotifyTemplateDO template = NotifyTemplateDO.builder().id(2L).sceneCode("test.scene").build();
        when(notifyTemplateService.getNotifyTemplateByCodeFromCache("TEST_TEMPLATE")).thenReturn(template);
        when(notifyRuleMapper.selectCount(any())).thenReturn(0L);
        stubValidCatalog();

        service.initializeDefaultRules(List.of(seed));

        ArgumentCaptor<NotifyRuleDO> captor = ArgumentCaptor.forClass(NotifyRuleDO.class);
        verify(notifyRuleMapper).insert(captor.capture());
        assertEquals("default rule", captor.getValue().getName());
        assertEquals("test.scene", captor.getValue().getSceneCode());
        assertEquals(List.of("owner"), captor.getValue().getRecipientRoles());
    }

    @Test
    void initializeDefaultRulesIsIdempotent() {
        NotifyDefaultRuleReqDTO seed = NotifyDefaultRuleReqDTO.builder().name("default rule")
                .sceneCode("test.scene").templateCode("TEST_TEMPLATE")
                .recipientRoles(List.of("owner")).actionType(NotifyActionType.MESSAGE_DETAIL).build();
        when(notifyTemplateService.getNotifyTemplateByCodeFromCache("TEST_TEMPLATE"))
                .thenReturn(NotifyTemplateDO.builder().id(2L).sceneCode("test.scene").build());
        when(notifyRuleMapper.selectCount(any())).thenReturn(1L);

        service.initializeDefaultRules(List.of(seed));

        verify(notifyRuleMapper, never()).insert(any(NotifyRuleDO.class));
    }

    @Test
    void initializeDefaultRulesAlsoCreatesWecomRuleWhenTemplateExists() {
        NotifyDefaultRuleReqDTO seed = NotifyDefaultRuleReqDTO.builder().name("default rule")
                .sceneCode("test.scene").templateCode("TEST_TEMPLATE")
                .recipientRoles(List.of("owner")).actionType(NotifyActionType.MESSAGE_DETAIL).build();
        NotifyTemplateDO inApp = NotifyTemplateDO.builder().id(2L).sceneCode("test.scene")
                .channelCode(NotifyChannelType.IN_APP).build();
        NotifyTemplateDO wecom = NotifyTemplateDO.builder().id(3L).sceneCode("test.scene")
                .channelCode(NotifyChannelType.WECOM).build();
        when(notifyTemplateService.getNotifyTemplateByCodeFromCache("TEST_TEMPLATE")).thenReturn(inApp);
        when(notifyTemplateService.getNotifyTemplateByCodeFromCache("TEST_TEMPLATE_WECOM")).thenReturn(wecom);
        when(notifyTemplateService.getNotifyTemplate(2L)).thenReturn(inApp);
        when(notifyTemplateService.getNotifyTemplate(3L)).thenReturn(wecom);
        when(notifyRuleMapper.selectCount(any())).thenReturn(0L);
        stubValidCatalog();

        service.initializeDefaultRules(List.of(seed));

        ArgumentCaptor<NotifyRuleDO> captor = ArgumentCaptor.forClass(NotifyRuleDO.class);
        verify(notifyRuleMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(NotifyChannelType.IN_APP, NotifyChannelType.WECOM),
                captor.getAllValues().stream().map(NotifyRuleDO::getChannelCode).toList());
    }

    private void stubValidCatalog() {
        when(sceneRegistry.getScene("test.scene")).thenReturn(scene());
        when(notifyTemplateService.getNotifyTemplate(2L))
                .thenReturn(NotifyTemplateDO.builder().id(2L).sceneCode("test.scene").build());
    }

    private void stubValidTimedCatalog() {
        when(sceneRegistry.getScene("test.scene")).thenReturn(timedScene());
        when(notifyTemplateService.getNotifyTemplate(2L))
                .thenReturn(NotifyTemplateDO.builder().id(2L).sceneCode("test.scene").build());
    }

    private static NotifySceneRespDTO scene() {
        return new NotifySceneRespDTO("test.scene", "测试场景", List.of(),
                List.of(new NotifySceneRoleRespDTO("owner", "负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL), false);
    }

    private static NotifySceneRespDTO timedScene() {
        return new NotifySceneRespDTO("test.scene", "测试场景", List.of(),
                List.of(new NotifySceneRoleRespDTO("owner", "负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL), true);
    }

    private static NotifyRuleSaveReqVO request() {
        NotifyRuleSaveReqVO request = new NotifyRuleSaveReqVO();
        request.setName("rule");
        request.setSceneCode("test.scene");
        request.setTemplateId(2L);
        request.setRecipientRoles(List.of("owner"));
        request.setSpecifiedUserIds(List.of());
        request.setActionType(NotifyActionType.MESSAGE_DETAIL);
        request.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return request;
    }
}
