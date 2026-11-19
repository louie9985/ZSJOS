package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistConfigRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistTemplateItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationRouteOptionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationChecklistTemplateItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationChecklistTemplateMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationChecklistVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationRouteOptionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistDraftSaveReqVO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationChecklistConfigServiceImplTest {
    @InjectMocks private RegistrationChecklistConfigServiceImpl service;
    @Mock private RegistrationChecklistTemplateMapper templateMapper;
    @Mock private RegistrationChecklistVersionMapper versionMapper;
    @Mock private RegistrationChecklistTemplateItemMapper itemMapper;
    @Mock private RegistrationRouteOptionMapper routeOptionMapper;
    @Mock private DeptApi deptApi;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getConfigDoesNotExposePublishedVersionAsDraft() {
        RegistrationChecklistTemplateDO template = template(2L, 2L, 2);
        RegistrationChecklistVersionDO published = version(2L, 2, "published");
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(published);
        when(itemMapper.selectByVersionId(2L)).thenReturn(List.of());
        when(routeOptionMapper.selectByVersionId(2L)).thenReturn(List.of());

        RegistrationChecklistConfigRespVO result = service.getConfig();

        assertNotNull(result.getPublished());
        assertNull(result.getDraft());
    }

    @Test
    void copyPublishedToDraftReplacesStalePublishedPointer() {
        RegistrationChecklistTemplateDO template = template(2L, 2L, 2);
        RegistrationChecklistVersionDO published = version(2L, 2, "published");
        RegistrationChecklistTemplateItemDO planner = new RegistrationChecklistTemplateItemDO()
                .setItemKey("study_planner").setItemType("study_planner").setTitle("配置学习规划师")
                .setSort(10).setEnabled(true).setSystemRequired(true).setAttachmentRequired(false);
        RegistrationChecklistTemplateItemDO item = new RegistrationChecklistTemplateItemDO()
                .setItemKey("verify").setItemType("checkbox").setTitle("核对报名资料")
                .setSort(20).setEnabled(true).setSystemRequired(false).setAttachmentRequired(false);
        RegistrationRouteOptionDO plannerRoute = new RegistrationRouteOptionDO().setOptionKey("student_delivery")
                .setDepartmentId(1050L).setDepartmentNameSnapshot("学生服务与交付中心")
                .setAssigneeType("study_planner").setSort(10).setEnabled(true).setSystemRequired(true);
        RegistrationRouteOptionDO route = new RegistrationRouteOptionDO().setOptionKey("new_media")
                .setDepartmentId(1060L).setDepartmentNameSnapshot("新媒体中心")
                .setAssigneeType("content_director").setSort(20).setEnabled(true).setSystemRequired(false);
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateMapper.selectByIdForUpdate(1L, 1L)).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(published);
        when(versionMapper.selectLatest(1L)).thenReturn(published);
        doAnswer(invocation -> {
            invocation.<RegistrationChecklistVersionDO>getArgument(0).setId(3L);
            return 1;
        }).when(versionMapper).insert(any(RegistrationChecklistVersionDO.class));
        when(itemMapper.selectByVersionId(2L)).thenReturn(List.of(planner, item));
        when(routeOptionMapper.selectByVersionId(2L)).thenReturn(List.of(plannerRoute, route));

        Long draftId = service.copyPublishedToDraft(2);

        assertEquals(3L, draftId);
        assertEquals(3L, template.getDraftVersionId());
        assertEquals(3, template.getVersion());
        // The retired study-planner item and route are dropped from the new draft.
        verify(itemMapper).insert(argThat((RegistrationChecklistTemplateItemDO copy) ->
                copy.getVersionId().equals(3L) && "checkbox".equals(copy.getItemType())));
        verify(routeOptionMapper).insert(argThat((RegistrationRouteOptionDO copy) ->
                copy.getVersionId().equals(3L) && "content_director".equals(copy.getAssigneeType())));
        verify(templateMapper).updateById(template);
    }

    @Test
    void copyPublishedToDraftSupportsMissingDraftPointer() {
        RegistrationChecklistTemplateDO template = template(2L, null, 2);
        RegistrationChecklistVersionDO published = version(2L, 2, "published");
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateMapper.selectByIdForUpdate(1L, 1L)).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(published);
        when(versionMapper.selectLatest(1L)).thenReturn(published);
        doAnswer(invocation -> {
            invocation.<RegistrationChecklistVersionDO>getArgument(0).setId(3L);
            return 1;
        }).when(versionMapper).insert(any(RegistrationChecklistVersionDO.class));
        when(itemMapper.selectByVersionId(2L)).thenReturn(List.of());
        when(routeOptionMapper.selectByVersionId(2L)).thenReturn(List.of());

        assertEquals(3L, service.copyPublishedToDraft(2));

        verify(versionMapper, times(1)).selectById(2L);
        verify(templateMapper).updateById(template);
    }

    @Test
    void publishClearsDraftPointerExplicitly() {
        RegistrationChecklistTemplateDO template = template(1L, 2L, 2);
        RegistrationChecklistVersionDO draft = version(2L, 2, "draft");
        RegistrationChecklistTemplateItemDO item = new RegistrationChecklistTemplateItemDO()
                .setItemKey("verify").setItemType("checkbox").setEnabled(true).setSystemRequired(false);
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateMapper.selectByIdForUpdate(1L, 1L)).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(draft);
        when(itemMapper.selectByVersionId(2L)).thenReturn(List.of(item));
        when(routeOptionMapper.selectByVersionId(2L)).thenReturn(List.of());
        when(templateMapper.publishDraft(1L, 2L, 2L, 2)).thenReturn(1);

        service.publish(2);

        assertEquals("published", draft.getStatus());
        assertNotNull(draft.getPublishedAt());
        verify(versionMapper).updateById(draft);
        verify(templateMapper).publishDraft(1L, 2L, 2L, 2);
        verify(templateMapper, never()).updateById(any(RegistrationChecklistTemplateDO.class));
    }

    @Test
    void publishRejectsDraftStillCarryingRetiredPlannerItem() {
        RegistrationChecklistTemplateDO template = template(1L, 2L, 2);
        RegistrationChecklistVersionDO draft = version(2L, 2, "draft");
        RegistrationChecklistTemplateItemDO planner = new RegistrationChecklistTemplateItemDO()
                .setItemKey("study_planner").setItemType("study_planner").setEnabled(true).setSystemRequired(true);
        RegistrationChecklistTemplateItemDO item = new RegistrationChecklistTemplateItemDO()
                .setItemKey("verify").setItemType("checkbox").setEnabled(true).setSystemRequired(false);
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateMapper.selectByIdForUpdate(1L, 1L)).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(draft);
        when(itemMapper.selectByVersionId(2L)).thenReturn(List.of(planner, item));
        when(routeOptionMapper.selectByVersionId(2L)).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> service.publish(2));
        verify(templateMapper, never()).publishDraft(any(), any(), any(), any());
    }

    @Test
    void saveDraftDropsRetiredPlannerItemAndAllowsNoRoutes() {
        RegistrationChecklistTemplateDO template = template(1L, 2L, 2);
        RegistrationChecklistVersionDO draft = version(2L, 2, "draft");
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateMapper.selectByIdForUpdate(1L, 1L)).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(draft);
        RegistrationChecklistDraftSaveReqVO.ItemReqVO planner = new RegistrationChecklistDraftSaveReqVO.ItemReqVO();
        planner.setItemKey("study_planner"); planner.setItemType("study_planner"); planner.setTitle("配置学习规划师");
        planner.setSort(10); planner.setEnabled(true);
        RegistrationChecklistDraftSaveReqVO.ItemReqVO item = new RegistrationChecklistDraftSaveReqVO.ItemReqVO();
        item.setItemKey("verify"); item.setItemType("checkbox"); item.setTitle("核对报名资料");
        item.setSort(20); item.setEnabled(true);
        RegistrationChecklistDraftSaveReqVO request = new RegistrationChecklistDraftSaveReqVO();
        request.setTemplateVersion(2); request.setItems(List.of(planner, item)); request.setRouteOptions(List.of());

        service.saveDraft(request);

        verify(itemMapper, times(1)).insert(argThat((RegistrationChecklistTemplateItemDO saved) ->
                "verify".equals(saved.getItemKey()) && "checkbox".equals(saved.getItemType())
                        && Boolean.FALSE.equals(saved.getSystemRequired())));
        verify(routeOptionMapper, never()).insert(any(RegistrationRouteOptionDO.class));
        verifyNoInteractions(deptApi);
    }

    @Test
    void saveDraftRejectsStudyPlannerRoute() {
        RegistrationChecklistTemplateDO template = template(1L, 2L, 2);
        RegistrationChecklistVersionDO draft = version(2L, 2, "draft");
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateMapper.selectByIdForUpdate(1L, 1L)).thenReturn(template);
        when(versionMapper.selectById(2L)).thenReturn(draft);
        RegistrationChecklistDraftSaveReqVO.ItemReqVO item = new RegistrationChecklistDraftSaveReqVO.ItemReqVO();
        item.setItemKey("verify"); item.setItemType("checkbox"); item.setTitle("核对报名资料");
        item.setSort(10); item.setEnabled(true);
        RegistrationChecklistDraftSaveReqVO.RouteOptionReqVO route = new RegistrationChecklistDraftSaveReqVO.RouteOptionReqVO();
        route.setOptionKey("student_delivery"); route.setDepartmentId(1050L); route.setAssigneeType("study_planner");
        route.setSort(10); route.setEnabled(true);
        RegistrationChecklistDraftSaveReqVO request = new RegistrationChecklistDraftSaveReqVO();
        request.setTemplateVersion(2); request.setItems(List.of(item)); request.setRouteOptions(List.of(route));

        assertThrows(ServiceException.class, () -> service.saveDraft(request));
        verify(itemMapper, never()).insert(any(RegistrationChecklistTemplateItemDO.class));
    }

    private static RegistrationChecklistTemplateDO template(Long publishedId, Long draftId, int version) {
        return new RegistrationChecklistTemplateDO().setId(1L).setPublishedVersionId(publishedId)
                .setDraftVersionId(draftId).setVersion(version);
    }

    private static RegistrationChecklistVersionDO version(Long id, int versionNo, String status) {
        return new RegistrationChecklistVersionDO().setId(id).setTemplateId(1L)
                .setVersionNo(versionNo).setStatus(status);
    }
}
