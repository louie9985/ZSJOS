package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanTemplateFieldSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanTemplateSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTemplateVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanTemplateFieldMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanTemplateItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanTemplateMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanTemplateScopeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanTemplateVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanTypeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkPlanTemplateServiceImplTest {
    @InjectMocks private WorkPlanTemplateServiceImpl service;
    @Mock private WorkPlanTypeMapper typeMapper;
    @Mock private WorkPlanTemplateMapper templateMapper;
    @Mock private WorkPlanTemplateVersionMapper versionMapper;
    @Mock private WorkPlanTemplateFieldMapper fieldMapper;
    @Mock private WorkPlanTemplateItemMapper itemMapper;
    @Mock private WorkPlanTemplateScopeMapper scopeMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;

    @Test
    void duplicateFieldKeyIsRejectedBeforeTemplatePersistence() {
        when(typeMapper.selectById(1L)).thenReturn(new WorkPlanTypeDO().setId(1L));
        WorkPlanTemplateSaveReqVO request = request(field("goal", "text"), field("goal", "textarea"));

        assertThrows(RuntimeException.class, () -> service.createTemplate(request, 9L));

        verify(templateMapper, never()).insert(any(WorkPlanTemplateDO.class));
        verify(versionMapper, never()).insert(any(WorkPlanTemplateVersionDO.class));
    }

    @Test
    void publishedTemplateCannotBeUpdatedWithoutCreatingANewDraftVersion() {
        when(typeMapper.selectById(1L)).thenReturn(new WorkPlanTypeDO().setId(1L));
        when(templateMapper.selectById(10L)).thenReturn(new WorkPlanTemplateDO().setId(10L).setStatus("published"));
        when(versionMapper.selectOne(any())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.updateTemplate(10L, request(field("goal", "text")), 9L));

        verify(templateMapper, never()).updateById(any(WorkPlanTemplateDO.class));
        verify(fieldMapper, never()).deleteHardByVersionId(any());
    }

    @Test
    void publishingDraftVersionPreservesItAsPublishedSnapshot() {
        WorkPlanTemplateDO template = new WorkPlanTemplateDO().setId(10L).setStatus("draft").setCurrentVersionNo(1);
        WorkPlanTemplateVersionDO version = new WorkPlanTemplateVersionDO().setId(20L).setTemplateId(10L)
                .setVersionNo(2).setStatus("draft");
        when(templateMapper.selectById(10L)).thenReturn(template);
        when(versionMapper.selectOne(any())).thenReturn(version);
        service.publishTemplate(10L, 9L);

        ArgumentCaptor<WorkPlanTemplateVersionDO> versionCaptor = ArgumentCaptor.forClass(WorkPlanTemplateVersionDO.class);
        verify(versionMapper).updateById(versionCaptor.capture());
        assertEquals("published", versionCaptor.getValue().getStatus());
        assertNotNull(versionCaptor.getValue().getPublishedAt());
        assertEquals("published", template.getStatus());
        assertEquals(2, template.getCurrentVersionNo());
        verify(templateMapper).updateById(template);
    }

    private WorkPlanTemplateSaveReqVO request(WorkPlanTemplateFieldSaveReqVO... fields) {
        return new WorkPlanTemplateSaveReqVO().setTypeId(1L).setCode("department_plan")
                .setName("部门计划").setPeriodMode("month")
                .setFields(List.of(fields)).setPresetItems(List.of()).setApplicableDeptIds(List.of());
    }

    private WorkPlanTemplateFieldSaveReqVO field(String key, String type) {
        return new WorkPlanTemplateFieldSaveReqVO().setFieldKey(key).setLabel(key)
                .setSection("plan").setFieldType(type).setRequired(false);
    }
}
