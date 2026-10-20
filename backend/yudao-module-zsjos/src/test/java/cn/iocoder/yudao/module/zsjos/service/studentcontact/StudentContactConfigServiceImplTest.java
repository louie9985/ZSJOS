package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactConfigCommandMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactConfigVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentContactConfigServiceImplTest {

    @InjectMocks private StudentContactConfigServiceImpl service;
    @Mock private StudentContactConfigVersionMapper mapper;
    @Mock private StudentContactConfigCommandMapper commandMapper;

    @Test
    void updateDraftRejectsSameKeyWithDifferentPayload() {
        StudentContactConfigSaveReqVO request = request();
        StudentContactConfigCommandDO existing = new StudentContactConfigCommandDO();
        existing.setOperation("update"); existing.setConfigId(request.getId());
        existing.setExpectedVersion(request.getVersion()); existing.setRequestFingerprint("different-payload");
        doThrow(new DuplicateKeyException("duplicate")).when(commandMapper)
                .insert(any(StudentContactConfigCommandDO.class));
        when(commandMapper.selectByIdempotencyKey(request.getIdempotencyKey())).thenReturn(existing);

        assertThrows(ServiceException.class, () -> service.updateDraft(request));
    }

    @Test
    void configReadAndDraftSaveExcludeRetiredItemWithoutChangingPublishedHistory() {
        var published = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO();
        published.setChecklistJson("[{\"key\":\"voice_learning_plan\",\"title\":\"旧学习计划\",\"enabled\":true},{\"key\":\"add_student\",\"title\":\"添加学员\",\"enabled\":true}]");
        published.setQuickNotesJson("[]"); published.setCollaboratorTabsJson("{}"); published.setFormsJson("{}");
        when(mapper.selectPublished()).thenReturn(published);
        org.junit.jupiter.api.Assertions.assertEquals(1, service.get().getPublished().getChecklist().size());
        org.junit.jupiter.api.Assertions.assertTrue(published.getChecklistJson().contains("voice_learning_plan"));
        var request=request(); var retired=new StudentContactConfigSaveReqVO.ChecklistItemReqVO();
        retired.setKey("voice_learning_plan");retired.setTitle("旧学习计划");retired.setEnabled(true);
        request.setChecklist(List.of(request.getChecklist().getFirst(),retired));
        when(mapper.updateDraft(any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(1);
        service.updateDraft(request);
        var draft=org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO.class);
        org.mockito.Mockito.verify(mapper).updateDraft(draft.capture(), org.mockito.ArgumentMatchers.eq(2));
        org.junit.jupiter.api.Assertions.assertFalse(draft.getValue().getChecklistJson().contains("voice_learning_plan"));
    }

    private StudentContactConfigSaveReqVO request() {
        StudentContactConfigSaveReqVO.ChecklistItemReqVO item = new StudentContactConfigSaveReqVO.ChecklistItemReqVO();
        item.setKey("add_student"); item.setTitle("添加学员"); item.setType("checkbox");
        item.setEnabled(true); item.setAttachmentRequired(false); item.setSort(1);
        StudentContactConfigSaveReqVO request = new StudentContactConfigSaveReqVO();
        request.setId(10L); request.setVersion(2); request.setIdempotencyKey("config-update-key");
        request.setFirstContactTimeoutMinutes(120); request.setStudyPlanTimeoutMinutes(1440);
        request.setChecklist(List.of(item)); request.setQuickNotes(List.of("已沟通"));
        request.setCollaboratorTabs(Map.of("content_director", List.of("contacts"),
                "career_planner", List.of("study-plan")));
        return request;
    }
}
