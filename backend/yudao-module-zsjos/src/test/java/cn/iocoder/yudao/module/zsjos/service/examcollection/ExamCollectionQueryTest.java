package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ExamCollectionQueryTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void historicalLabelsAreReadFromSnapshots() {
        var answer = new StudentInfoFormValueDO(); answer.setFieldKey("education_level"); answer.setValueCode("old"); answer.setValueLabelSnapshot("原始学历标签");
        assertEquals("原始学历标签", ExamCollectionQueryService.display(List.of(answer)).get("education_level"));
    }
    @Test void detailCannotReadOtherTaskTenantOrDraftAndFileMustBeInSubmittedSelection() {
        TenantContextHolder.setTenantId(1L); var service = new ExamCollectionQueryService();
        var objects = mock(ExamCollectionPermissionProvider.class); var submissions = mock(ExamCollectionSubmissionMapper.class);
        var files = mock(ExamCollectionAttachmentMapper.class);
        ReflectionTestUtils.setField(service, "objects", objects); ReflectionTestUtils.setField(service, "submissions", submissions);
        ReflectionTestUtils.setField(service, "attachments", files);
        var row = new ExamCollectionSubmissionDO(); row.setId(2L); row.setTaskId(9L); row.setTenantId(1L); row.setSubmittedAt(LocalDateTime.now());
        when(submissions.selectById(2L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.submission(1L, 2L));
        row.setTaskId(1L); row.setTenantId(2L); assertThrows(ServiceException.class, () -> service.submission(1L, 2L));
        row.setTenantId(1L); row.setSubmittedAt(null); assertThrows(ServiceException.class, () -> service.submission(1L, 2L));
        row.setSubmittedAt(LocalDateTime.now()); row.setAttachmentIdsJson("[3]");
        assertThrows(ServiceException.class, () -> service.attachment(1L, 2L, 4L));
        var file = new ExamCollectionAttachmentDO(); file.setTaskId(1L); file.setTenantId(1L); file.setSubmissionId(99L);
        when(files.selectById(3L)).thenReturn(file);
        assertThrows(ServiceException.class, () -> service.attachment(1L, 2L, 3L));
    }
    @Test @SuppressWarnings("unchecked") void unassignedCandidateDoesNotRequireClassOrPersonLookup() {
        var service = new ExamCollectionQueryService();
        var objects = mock(ExamCollectionPermissionProvider.class);
        var relations = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper.class);
        ReflectionTestUtils.setField(service, "objects", objects); ReflectionTestUtils.setField(service, "relations", relations);
        var row = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO(); row.setId(1L); row.setStudentNameSnapshot("测试学员");
        var query = new cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.CandidateQuery();
        when(relations.selectPage(eq(query), any(cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX.class)))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(row), 1L));
        var result = service.candidates(9L, query);
        assertEquals("测试学员", result.getList().getFirst().studentName());
        assertNull(result.getList().getFirst().className()); assertNull(result.getList().getFirst().mobile());
    }
}
