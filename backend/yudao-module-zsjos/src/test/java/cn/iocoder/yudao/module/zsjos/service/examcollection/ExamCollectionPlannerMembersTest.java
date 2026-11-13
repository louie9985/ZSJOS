package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionPlannerVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionMemberDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionMemberMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ExamCollectionPlannerMembersTest {
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void rosterQueryBindsCurrentPlannerBeforePaginationAndProjectsArchive(){
        TenantContextHolder.setTenantId(1L);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"exam-planner"),ExamCollectionMemberDO.class);
        var service=new ExamCollectionReminderService();
        var recipientScope = new ExamCollectionRecipientScope();
        ReflectionTestUtils.setField(recipientScope, "permissions", mock(PermissionApi.class));
        ReflectionTestUtils.setField(service, "recipientScope", recipientScope);var permissions=mock(PermissionApi.class);var objects=mock(ExamCollectionPlannerPermissionProvider.class);
        var members=mock(ExamCollectionMemberMapper.class);var relations=mock(ServiceRelationMapper.class);var people=mock(PersonMapper.class);
        ReflectionTestUtils.setField(service,"permissions",permissions);ReflectionTestUtils.setField(service,"plannerPermissions",objects);ReflectionTestUtils.setField(service,"members",members);ReflectionTestUtils.setField(service,"relations",relations);ReflectionTestUtils.setField(service,"people",people);
        var summary=mock(cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService.class);
        ReflectionTestUtils.setField(service,"summary",summary);
        var member=new ExamCollectionMemberDO();member.setServiceRelationId(10L);member.setPersonId(20L);member.setClassNameSnapshot("通知时的班级");
        when(members.selectPage(any(ExamCollectionPlannerVO.Query.class),any(LambdaQueryWrapperX.class))).thenReturn(new PageResult<>(List.of(member),1L));
        var relation=new ServiceRelationDO();relation.setId(10L);relation.setStudentNameSnapshot("合成学员");when(relations.selectByIds(anyCollection())).thenReturn(List.of(relation));
        when(summary.projectForExamPlanner(eq(1L),anyList())).thenReturn(List.of(new cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.Row(10L, java.util.Map.of("info.name","档案姓名","info.mobile","13900000000","info.id_card","fixture-identity"))));
        var person=new PersonDO();person.setId(20L);person.setMobile("13800000000");when(people.selectByIds(anyCollection())).thenReturn(List.of(person));
        when(permissions.hasAnyPermissions(7L,"zsjos:business-task:query")).thenReturn(true);
        try(var security=mockStatic(SecurityFrameworkUtils.class)){
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var query=new ExamCollectionPlannerVO.Query();query.setFilled(false);query.setKeyword("' OR 1=1");
            var result=service.members(1L,query);assertEquals("通知时的班级",result.getList().getFirst().className());assertFalse(result.getList().getFirst().filled());
            assertEquals("档案姓名",result.getList().getFirst().archiveName());
            assertEquals("13900000000",result.getList().getFirst().archiveMobile());
            assertEquals("fixture-identity",result.getList().getFirst().archiveIdCard());
            verify(summary).projectForExamPlanner(1L,List.of(relation));
            var capture=ArgumentCaptor.forClass(LambdaQueryWrapperX.class);verify(members).selectPage(eq(query),capture.capture());
            String sql=capture.getValue().getSqlSegment();assertTrue(sql.contains("planner_user_id"));assertTrue(sql.contains("last_submission_id IS NULL"));assertFalse(sql.contains(query.getKeyword()));
            assertTrue(capture.getValue().getParamNameValuePairs().containsValue(7L));assertTrue(capture.getValue().getParamNameValuePairs().containsValue(query.getKeyword()));
            String json=JsonUtils.toJsonString(result.getList());assertFalse(json.contains("identityHash"));assertFalse(json.contains("id_card"));assertFalse(json.contains("answers"));
            verify(objects).check(1L,"query",7L);
            var second = new ExamCollectionMemberDO();
            second.setServiceRelationId(11L);second.setPersonId(20L);second.setClassNameSnapshot("A班");
            when(members.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(member, second));
            query.setSortField("className");query.setSortOrder("asc");query.setPageSize(1);query.setPageNo(1);
            var firstPage = service.members(1L, query);
            assertEquals(2L, firstPage.getTotal());assertEquals(11L, firstPage.getList().getFirst().serviceRelationId());
            query.setPageNo(2);
            assertEquals(10L, service.members(1L, query).getList().getFirst().serviceRelationId());
            query.setSortOrder("desc");query.setPageNo(1);
            assertEquals(10L, service.members(1L, query).getList().getFirst().serviceRelationId());
        }
    }
}
