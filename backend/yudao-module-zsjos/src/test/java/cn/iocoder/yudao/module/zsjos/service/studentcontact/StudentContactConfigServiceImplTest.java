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
