package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentSummaryServiceTest {
    StudentSummaryService service=new StudentSummaryService();
    PermissionApi permissions=mock(PermissionApi.class);
    StudentServicePageMapper services=mock(StudentServicePageMapper.class);
    StudentInfoFormMapper forms=mock(StudentInfoFormMapper.class);
    StudentSummaryMapper bindings=mock(StudentSummaryMapper.class);
    StudentInfoFormValueMapper answers=mock(StudentInfoFormValueMapper.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        for(var field:StudentSummaryService.class.getDeclaredFields()) if(field.isAnnotationPresent(jakarta.annotation.Resource.class))
            ReflectionTestUtils.setField(service,field.getName(),mock(field.getType()));
        ReflectionTestUtils.setField(service,"permissions",permissions); ReflectionTestUtils.setField(service,"services",services);
        ReflectionTestUtils.setField(service,"forms",forms); ReflectionTestUtils.setField(service,"summaryMapper",bindings);
        ReflectionTestUtils.setField(service,"answers",answers); ReflectionTestUtils.setField(service,"fields",new StudentInfoFields());
        when(permissions.hasAnyPermissions(any(),eq(StudentSummaryService.QUERY))).thenReturn(true);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void unauthorizedDoesNotQuery() {
        when(permissions.hasAnyPermissions(any(),eq(StudentSummaryService.QUERY))).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.page(new Query())); verifyNoInteractions(services);
    }
    @Test void classKeywordAndTenantAreBoundParameters() {
        var q=new Query(); q.setClassId(5L); q.setKeyword("' OR 1=1");
        service.page(q);
        var capture=org.mockito.ArgumentCaptor.forClass(AdvancedFilterQuery.class);verify(services).count(capture.capture());
        assertEquals(1L,capture.getValue().getParameters().get("tenantId"));
        assertEquals(5L,capture.getValue().getParameters().get("classId"));
        assertFalse(capture.getValue().getWhereSql().contains(q.getKeyword()));
        assertFalse(capture.getValue().getWhereSql().contains("owner_user_id"));
    }
    @Test void coverageUsesSelectedClassesWithoutSearchOrPagination() {
        var q=new Query();q.setClassIds(List.of(5L,6L));q.setKeyword("search");q.setPageNo(3);
        when(services.count(any())).thenReturn(45L,66L);
        assertEquals(new Coverage(45,66),service.coverage(q));
        var capture=org.mockito.ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(services,times(2)).count(capture.capture());
        var queries=capture.getAllValues();
        assertTrue(queries.getFirst().getWhereSql().contains("submitted.status='SUBMITTED'"));
        assertFalse(queries.getLast().getWhereSql().contains("submitted.status"));
        for(var query:queries) {
            assertEquals(5L,query.getParameters().get("selectedClass0"));
            assertEquals(6L,query.getParameters().get("selectedClass1"));
            assertFalse(query.getParameters().containsKey("keyword"));
            assertEquals(0,query.getParameters().get("offset"));
        }
    }
    @Test void generatedSqlParsesForDefaultAndFilteredQueries() {
        var filtered=new Query(); filtered.setClassIds(List.of(5L,6L)); filtered.setKeyword("test");
        var filled=new Query();filled.setFilled(true);
        var unfilled=new Query();unfilled.setFilled(false);
        for(var request:List.of(new Query(),filtered,filled,unfilled)) {
            clearInvocations(services);
            service.page(request); service.coverage(request);
            var capture=org.mockito.ArgumentCaptor.forClass(AdvancedFilterQuery.class);
            verify(services,times(3)).count(capture.capture());
            var actual=capture.getAllValues().getFirst().getWhereSql();
            if(request.getFilled()==null) assertFalse(actual.contains("submitted.status"));
            else assertTrue(actual.contains(request.getFilled()?"AND EXISTS":"AND NOT EXISTS"));
            for(var query:capture.getAllValues()) {
                var params=Map.<String,Object>of("query",query);
                for(var sql:List.of(
                    cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageSql.count(params),
                    cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageSql.page(params))) {
                    var configuration=new org.apache.ibatis.session.Configuration();
                    var source=new org.apache.ibatis.scripting.xmltags.XMLLanguageDriver().createSqlSource(configuration,sql,Map.class);
                    var bound=source.getBoundSql(params).getSql();
                    assertDoesNotThrow(()->net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(bound));
                }
            }
        }
    }
    void row(boolean submitted) {
        var relation=new ServiceRelationDO();relation.setId(1L);relation.setPersonId(2L);relation.setOrderItemId(3L);relation.setRegistrationCaseId(4L);relation.setStudentNameSnapshot("测试学员");relation.setStatus("active");
        when(services.count(any())).thenReturn(1L);when(services.page(any())).thenReturn(List.of(relation));
        var binding=new StudentSummaryMapper.Binding();binding.setServiceId(1L);binding.setFormId(6L);when(bindings.bindings(anyList(),eq(1L))).thenReturn(List.of(binding));
        var form=new StudentInfoFormDO();form.setId(6L);form.setPersonId(2L);form.setOrderItemId(3L);form.setStatus(submitted?"SUBMITTED":"DRAFT");when(forms.selectByIds(anyCollection())).thenReturn(List.of(form));
        var value=new StudentInfoFormValueDO();value.setFormId(6L);value.setFieldKey("id_card");value.setValueText("TEST-IDENTITY-TEXT");value.setSensitive(true);
        when(answers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(value));
    }
    @Test void summaryGrantReadsSensitiveWithoutLegacyPermission() {
        row(true);var row=service.page(new Query()).getList().getFirst();
        assertEquals("TEST-IDENTITY-TEXT",row.values().get("info.id_card"));assertFalse(row.values().containsKey("mobile"));
        verify(permissions,never()).hasAnyPermissions(any(),eq("zsjos:student-archive:sensitive-read"));
    }
    @Test void examRevisionOverridesOldDraftAndWorksWithoutOriginalForm() {
        row(false);
        var archiveMapper=(cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.StudentServiceArchiveRevisionMapper)ReflectionTestUtils.getField(service,"serviceRevisions");
        var submissionMapper=(cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionSubmissionMapper)ReflectionTestUtils.getField(service,"examSubmissions");
        var revision=new cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.StudentServiceArchiveRevisionDO();
        revision.setServiceRelationId(1L);revision.setPersonId(2L);revision.setRevision(1);revision.setSubmissionId(9L);revision.setSource("exam_submission");
        var value=new StudentInfoFormValueDO();value.setFieldKey("name");value.setValueText("考试提交姓名");
        revision.setAnswersJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(List.of(value)));
        revision.setAttachmentsJson("[]");
        when(archiveMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(revision));
        var submission=new cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionSubmissionDO();submission.setId(9L);submission.setServiceRelationId(1L);submission.setSubmittedAt(java.time.LocalDateTime.of(2026,11,4,9,0));
        when(submissionMapper.selectByIds(anyCollection())).thenReturn(List.of(submission));
        var result=service.page(new Query()).getList().getFirst();assertEquals("考试提交姓名",result.values().get("info.name"));assertEquals("已填写",result.values().get("collectionStatus"));assertEquals("2026-11-04 09:00:00",result.values().get("submittedAt"));
        when(bindings.bindings(anyList(),eq(1L))).thenReturn(List.of());
        assertEquals("考试提交姓名",service.page(new Query()).getList().getFirst().values().get("info.name"));
    }
    @Test void unsubmittedKeepsStudentWithoutDraftAnswers() {
        row(false);var values=service.page(new Query()).getList().getFirst().values();assertEquals("测试学员",values.get("student.name"));assertEquals("未填写",values.get("collectionStatus"));assertFalse(values.containsKey("info.id_card"));verifyNoInteractions(answers);
    }
    @Test void wrongCourseRejected() {
        row(true);var form=new StudentInfoFormDO();form.setId(6L);form.setPersonId(2L);form.setOrderItemId(999L);form.setStatus("SUBMITTED");when(forms.selectByIds(anyCollection())).thenReturn(List.of(form));
        assertThrows(RuntimeException.class,()->service.page(new Query()));
    }
    @Test void exportRequiresBothPermissions() {
        var request=new Export();request.setColumns(List.of("name"));assertThrows(RuntimeException.class,()->service.export(request,new MockHttpServletResponse()));verifyNoInteractions(services);
    }
    @Test void excelMatchesSelectedOrderAndText() throws Exception {
        row(true);when(permissions.hasAnyPermissions(any(),eq(StudentSummaryService.EXPORT))).thenReturn(true);
        var request=new Export();request.setColumns(List.of("info.id_card","collectionStatus"));var response=new MockHttpServletResponse();service.export(request,response);
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=workbook.getSheetAt(0);assertEquals(2,sheet.getRow(0).getLastCellNum());
            assertEquals("身份证号码",sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("TEST-IDENTITY-TEXT",sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("已填写",sheet.getRow(1).getCell(1).getStringCellValue());
        }
    }
    @Test void catalogContainsComparisonAndArchiveFields() {
        var keys=service.columns().stream().map(Column::key).toList();
        assertTrue(keys.containsAll(List.of("info.name","info.id_card","extra.theory")));
        assertTrue(keys.stream().allMatch(k->k.startsWith("info.") || k.startsWith("extra.") || Set.of("student.name","student.mobile","collectionStatus","submittedAt").contains(k)));
        assertThrows(RuntimeException.class,()->StudentSummaryService.selectedColumns(service.columns(),List.of("serviceStatus")));
    }
    @Test void studentContactsRemainDistinctFromSubmittedAnswersAndExport() throws Exception {
        row(true);
        var people=mock(PersonMapper.class); ReflectionTestUtils.setField(service,"people",people);
        var person=new PersonDO();person.setId(2L);person.setName("不用此主档姓名");person.setMobile("13800000001");
        when(people.selectByIds(anyCollection())).thenReturn(List.of(person));
        var name=new StudentInfoFormValueDO();name.setFormId(6L);name.setFieldKey("name");name.setValueText("档案姓名");
        var mobile=new StudentInfoFormValueDO();mobile.setFormId(6L);mobile.setFieldKey("mobile");mobile.setValueText("13800000002");
        when(answers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(name,mobile));
        var keys=List.of("student.name","student.mobile","info.name","info.mobile");
        var expected=List.of("测试学员","13800000001","档案姓名","13800000002");
        var values=service.page(new Query()).getList().getFirst().values();
        assertEquals(expected,keys.stream().map(values::get).toList());
        assertEquals(keys,service.columns().stream().limit(4).map(Column::key).toList());
        when(permissions.hasAnyPermissions(any(),eq(StudentSummaryService.EXPORT))).thenReturn(true);
        var request=new Export();request.setColumns(keys);var response=new MockHttpServletResponse();service.export(request,response);
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=workbook.getSheetAt(0);
            assertEquals("学员·姓名",sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("学员·手机号",sheet.getRow(0).getCell(1).getStringCellValue());
            for(int col=0;col<4;col++) assertEquals(expected.get(col),sheet.getRow(1).getCell(col).getStringCellValue());
        }
    }
    @Test void unknownOrDuplicateColumnsRejected() {
        var catalog=service.columns();assertThrows(RuntimeException.class,()->StudentSummaryService.selectedColumns(catalog,List.of("password")));
        assertThrows(RuntimeException.class,()->StudentSummaryService.selectedColumns(catalog,List.of("info.name","info.name")));
    }
}
