package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class ExamCollectionSummaryTest {
    ExamCollectionSummaryService service;
    ExamCollectionMemberMapper members;
    ExamCollectionSubmissionMapper submissions;
    ServiceRelationMapper relations;
    StudentSummaryService projection;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);service=new ExamCollectionSummaryService();
        members=inject("members",ExamCollectionMemberMapper.class);submissions=inject("submissions",ExamCollectionSubmissionMapper.class);
        relations=inject("relations",ServiceRelationMapper.class);projection=inject("summary",StudentSummaryService.class);
        inject("permissions",ExamCollectionPermissionProvider.class);
    }
    <T>T inject(String field,Class<T>type){T value=mock(type);ReflectionTestUtils.setField(service,field,value);return value;}
    @AfterEach void clear(){TenantContextHolder.clear();}
    void member(boolean submitted){
        var row=new ExamCollectionMemberDO();row.setServiceRelationId(10L);row.setPersonId(20L);row.setClassId(4L);if(submitted)row.setLastSubmissionId(9L);
        when(members.selectPage(any(PageParam.class),any(LambdaQueryWrapperX.class))).thenReturn(new PageResult<>(List.of(row),1L));
        var relation=new ServiceRelationDO();relation.setId(10L);relation.setPersonId(20L);relation.setTenantId(1L);relation.setClassId(99L);
        when(relations.selectByIds(anyCollection())).thenReturn(List.of(relation));
        when(projection.projectForExam(eq(1L),anyList())).thenAnswer(call->List.of(new Row(10L,new LinkedHashMap<>(Map.of("info.name","已有报名档案","collectionStatus","已填写","submittedAt","旧报名日期")))));
    }
    @Test void previousArchiveDoesNotCountAsCurrentTaskSubmission(){
        member(false);var request=new Query();request.setClassIds(List.of(4L));
        var result=service.page(1L,request).getList().getFirst();
        assertEquals("未填写",result.values().get("collectionStatus"));assertEquals("",result.values().get("submittedAt"));
        assertEquals("已有报名档案",result.values().get("info.name"));
    }
    @Test void currentTaskSubmissionSuppliesItsOwnTime(){
        member(true);var row=new ExamCollectionSubmissionDO();row.setId(9L);row.setSubmittedAt(java.time.LocalDateTime.of(2026,11,4,10,0));
        when(submissions.selectByIds(anyCollection())).thenReturn(List.of(row));
        var result=service.page(1L,new Query()).getList().getFirst();assertEquals("已填写",result.values().get("collectionStatus"));assertEquals("2026-11-04 10:00:00",result.values().get("submittedAt"));
    }
    @Test void exportUsesCurrentTaskStatusAndSelectedColumnOrder()throws Exception{
        member(false);when(projection.columnsForExam(1L)).thenReturn(List.of(new Column("info.name","姓名","报名资料"),new Column("collectionStatus","填写状态","档案状态")));
        var request=new Export();request.setColumns(List.of("collectionStatus","info.name"));var response=new MockHttpServletResponse();service.export(1L,request,response);
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))){var sheet=workbook.getSheetAt(0);assertEquals("填写状态",sheet.getRow(0).getCell(0).getStringCellValue());assertEquals("未填写",sheet.getRow(1).getCell(0).getStringCellValue());assertEquals("已有报名档案",sheet.getRow(1).getCell(1).getStringCellValue());}
    }
}
