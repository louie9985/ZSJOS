package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    private void stubValidCatalog() {
        when(sceneRegistry.getScene("test.scene")).thenReturn(scene());
        when(notifyTemplateService.getNotifyTemplate(2L))
                .thenReturn(NotifyTemplateDO.builder().id(2L).sceneCode("test.scene").build());
    }

    private static NotifySceneRespDTO scene() {
        return new NotifySceneRespDTO("test.scene", "测试场景", List.of(),
                List.of(new NotifySceneRoleRespDTO("owner", "负责人")),
                List.of(NotifyActionType.MESSAGE_DETAIL));
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
