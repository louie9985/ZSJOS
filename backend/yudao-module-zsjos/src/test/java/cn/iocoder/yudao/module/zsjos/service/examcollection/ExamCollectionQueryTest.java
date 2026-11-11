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
    @Test @SuppressWarnings("unchecked") void memberPageSeparatesStudentContactsFromBatchFormalArchiveValues() {
        var service = new ExamCollectionQueryService();
        var members = mock(ExamCollectionMemberMapper.class);
        var relations = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper.class);
        var summary = mock(cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService.class);
        var archives = mock(StudentServiceArchiveStore.class);
        ReflectionTestUtils.setField(service,"objects",mock(ExamCollectionPermissionProvider.class));
        ReflectionTestUtils.setField(service,"members",members); ReflectionTestUtils.setField(service,"relations",relations);
        ReflectionTestUtils.setField(service,"summary",summary); ReflectionTestUtils.setField(service,"archives",archives);
        var first = new ExamCollectionMemberDO(); first.setServiceRelationId(10L); first.setPersonId(20L); first.setActive(true);
        first.setIdentityHash("never-use-frozen-hash-as-identity");
        var second = new ExamCollectionMemberDO(); second.setServiceRelationId(11L); second.setPersonId(21L); second.setActive(true);
        var one = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO(); one.setId(10L); one.setStudentNameSnapshot("系统姓名");
        var two = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO(); two.setId(11L); two.setStudentNameSnapshot("无档案学员");
        var query = new cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.MemberQuery();
        when(members.selectPage(eq(query),any(cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX.class)))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(first,second),2L));
        when(relations.selectByIds(anyCollection())).thenReturn(List.of(one,two));
        when(summary.projectForExam(eq(1L),anyList())).thenReturn(List.of(
                new cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.Row(10L,java.util.Map.of(
                        "student.mobile","13800000000","info.name","档案姓名","info.mobile","13900000000","info.id_card","SYNTHETIC-ID")),
                new cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.Row(11L,java.util.Map.of("student.mobile","13800000001"))));
        var result = service.members(1L,query).getList();
        assertEquals("系统姓名",result.getFirst().studentName()); assertEquals("13800000000",result.getFirst().mobile());
        assertEquals("档案姓名",result.getFirst().archiveName()); assertEquals("13900000000",result.getFirst().archiveMobile());
        assertEquals("SYNTHETIC-ID",result.getFirst().archiveIdCard());
        assertNull(result.get(1).archiveName());assertNull(result.get(1).archiveMobile());assertNull(result.get(1).archiveIdCard());
        verify(summary,times(1)).projectForExam(eq(1L),argThat(selected -> selected.size()==2));
        verifyNoInteractions(archives);
    }

    @Test @SuppressWarnings("unchecked") void emptyMemberPageDoesNotReadAnyArchive() {
        var service = new ExamCollectionQueryService(); var members = mock(ExamCollectionMemberMapper.class);
        var summary = mock(cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService.class);
        ReflectionTestUtils.setField(service,"objects",mock(ExamCollectionPermissionProvider.class));
        ReflectionTestUtils.setField(service,"members",members);ReflectionTestUtils.setField(service,"summary",summary);
        var query = new cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.MemberQuery();
        when(members.selectPage(eq(query),any(cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX.class)))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(),0L));
        assertTrue(service.members(1L,query).getList().isEmpty());verifyNoInteractions(summary);
    }
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
