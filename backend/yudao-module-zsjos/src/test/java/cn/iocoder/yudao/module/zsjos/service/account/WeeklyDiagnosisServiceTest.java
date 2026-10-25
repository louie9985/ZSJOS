package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyDiagnosisServiceTest {
    @InjectMocks WeeklyDiagnosisService service;
    @Mock MediaAccountMapper accounts; @Mock MediaAccountDiagnosisReminderService reminders;
    @Mock MediaAccountProfileService profiles; @Mock MediaAccountProfileEntryMapper entries;
    @Mock BusinessTaskMapper tasks; @Mock BusinessTaskCommandService commands;
    @Mock DiagnosisMetricService metrics; @Mock DictDataApi dicts;
    MediaAccountDO account;LocalDateTime anchor;
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);anchor=DiagnosisMetricService.now().minusDays(28).toLocalDate().atStartOfDay();
        account=new MediaAccountDO().setId(10L).setDirectorUserId(20L).setDetailValuesJson(JsonUtils.toJsonString(Map.of("_diagnosisContext",JsonUtils.toJsonString(Map.of("anchorAt",anchor.toString(),"roundKey","r")))));}
    @AfterEach void clear(){TenantContextHolder.clear();}
    DiagnosisTodo todo(int days,int cycle,long id){return new DiagnosisTodo(id,10L,1L,"测试","诊断","diagnosis_"+days+"d",cycle,anchor.plusDays((long)days*cycle),Map.of());}
    @Test void sameDeadlineAggregatesAcrossDifferentTemplateCycles(){
        when(accounts.selectById(10L)).thenReturn(account);
        when(reminders.accountTasks(10L,20L)).thenReturn(List.of(todo(7,4,1),todo(14,2,2),todo(28,1,3),todo(14,3,4)));
        when(metrics.read(any(),any(),any(),any(),isNull(),eq(false),any(DiagnosisMetricService.ReadContext.class))).thenAnswer(i->{var m=new Metric();m.setState("OPEN");return m;});
        var nodes=service.nodes(10L,20L);assertEquals(1,nodes.size());
        assertEquals(4,nodes.getFirst().week());assertEquals(List.of(7,14,28),nodes.getFirst().parts().stream().map(Part::days).toList());
        assertEquals(anchor.plusDays(14),nodes.getFirst().parts().get(1).from());
    }
    @Test void oldOverdueNodesStaySeparate(){
        when(accounts.selectById(10L)).thenReturn(account);when(reminders.accountTasks(10L,20L)).thenReturn(List.of(todo(7,1,1),todo(7,2,2),todo(14,1,3)));
        when(metrics.read(any(),any(),any(),any(),isNull(),eq(false),any(DiagnosisMetricService.ReadContext.class))).thenReturn(new Metric());
        var nodes=service.nodes(10L,20L);assertEquals(2,nodes.size());assertEquals(1,nodes.getFirst().parts().size());assertEquals(2,nodes.get(1).parts().size());
    }
    @Test void nonDirectorCannotReadNodesOrReachMetrics(){
        when(accounts.selectById(10L)).thenReturn(account);assertThrows(RuntimeException.class,()->service.nodes(10L,99L));verifyNoInteractions(reminders,metrics);
    }
    @Test void overviewKeepsFutureDeadlinesButOnlyOpensStartedNodesWithoutMetrics(){
        when(accounts.selectById(10L)).thenReturn(account);
        var pending=List.of(todo(7,4,1),todo(14,2,2),todo(28,1,3),todo(14,3,4));
        when(reminders.accountTasks(10L,20L)).thenReturn(pending);
        var overview=service.overview(10L,20L);
        assertEquals(pending,overview.tasks());assertEquals(1,overview.nodes().size());
        assertEquals(List.of(1L,2L,3L),overview.nodes().getFirst().taskIds());
        assertTrue(overview.nodes().getFirst().parts().stream().allMatch(part->part.metric()==null));
        verify(reminders,times(1)).accountTasks(10L,20L);verifyNoInteractions(metrics);
    }
    @Test void overviewDeniesAnotherDirectorBeforeReadingTasks(){
        when(accounts.selectById(10L)).thenReturn(account);
        assertThrows(RuntimeException.class,()->service.overview(10L,99L));verifyNoInteractions(reminders,metrics);
    }
    @Test void overviewWithoutPendingTasksIsEmpty(){
        when(accounts.selectById(10L)).thenReturn(account);when(reminders.accountTasks(10L,20L)).thenReturn(List.of());
        var overview=service.overview(10L,20L);assertTrue(overview.tasks().isEmpty());assertTrue(overview.nodes().isEmpty());verifyNoInteractions(metrics);
    }
    @Test void overviewWithoutAnchorKeepsTasksButDoesNotInventNodes(){
        account.setDetailValuesJson("{}");when(accounts.selectById(10L)).thenReturn(account);
        when(reminders.accountTasks(10L,20L)).thenReturn(List.of(todo(7,1,1)));
        var overview=service.overview(10L,20L);assertEquals(1,overview.tasks().size());assertTrue(overview.nodes().isEmpty());verifyNoInteractions(metrics);
    }
    @Test void overviewPropagatesObjectDenialWithoutStatistics(){
        when(accounts.selectById(10L)).thenReturn(account);
        when(reminders.accountTasks(10L,20L)).thenThrow(new IllegalStateException("object denied"));
        assertThrows(IllegalStateException.class,()->service.overview(10L,20L));verifyNoInteractions(metrics);
    }
    @Test void replayRequiresIdenticalWeeklyFingerprintAndNeverReappliesState(){
        when(accounts.selectByIdForUpdate(10L,1L)).thenReturn(account);
        var request=new Submit();request.setNodeKey("node");var common=new DiagnosisRequest();common.setIdempotencyKey("key");request.setCommon(common);
        var saved=new MediaAccountProfileEntryDO();saved.setContent("{\"weeklyFingerprint\":\"different\"}");
        when(entries.replay(10L,20L,"key")).thenReturn(saved);
        assertThrows(RuntimeException.class,()->service.submit(10L,request,20L));verifyNoInteractions(profiles,commands,metrics);
    }
    @Test void wholeNodeUsesOneStateUpdateAndCompletesAllRemainingTasks(){
        when(accounts.selectById(10L)).thenReturn(account);when(accounts.selectByIdForUpdate(10L,1L)).thenReturn(account);
        when(reminders.accountTasks(10L,20L)).thenReturn(List.of(todo(7,4,1),todo(14,2,2),todo(28,1,3)));
        when(metrics.read(any(),any(),any(),any(),any(),anyBoolean(),any(DiagnosisMetricService.ReadContext.class))).thenAnswer(i->{var m=new Metric();m.setState("OPEN");return m;});
        var preview=service.preview(10L,WeeklyDiagnosisPeriods.key(10L,"r",anchor.plusDays(28)),0L,20L);
        var common=new DiagnosisRequest();common.setIdempotencyKey("idempotency");
        var request=new Submit();request.setCommon(common);request.setNodeKey(preview.node().key());request.setMetricToken(preview.metricToken());
        request.setHighestLikes(0L);request.setChangeDirection("true");request.setTrafficNormal("false");request.setDirectionSummary("概况");
        var yes=new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO();yes.setValue("true");yes.setLabel("是");
        var no=new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO();no.setValue("false");no.setLabel("否");
        when(dicts.getDictDataList("infra_boolean_string")).thenReturn(List.of(yes,no));
        for(long id=1;id<=3;id++){var task=new BusinessTaskDO();task.setId(id);task.setStatus("pending");task.setAssigneeId(20L);task.setBizId(10L);task.setIdempotencyKey("task"+id);
            when(tasks.selectByIdForUpdate(id,1L)).thenReturn(task);if(id>1)when(tasks.selectById(id)).thenReturn(task);}
        var written=new MediaAccountProfileEntryDO();written.setId(101L);written.setContent("{}");written.setOperatedByName("测试");
        when(entries.replay(10L,20L,"idempotency")).thenReturn(null,written);
        when(profiles.submitDiagnosis(10L,common,20L)).thenReturn(2);when(commands.completeByKey(anyString(),any())).thenReturn(true);
        assertEquals(2,service.submit(10L,request,20L));verify(profiles,times(1)).submitDiagnosis(10L,common,20L);
        verify(entries,times(2)).insert(any(MediaAccountProfileEntryDO.class));verify(commands).completeByKey(eq("task2"),any());verify(commands).completeByKey(eq("task3"),any());
        var saved=JsonUtils.parseObject(written.getContent(),Map.class);assertEquals(0,((Number)saved.get("highestLikes")).intValue());assertEquals("是",saved.get("changeDirectionLabel"));
    }
    @Test void historyReadsMetricsWithoutProjectingProfile(){
        var part=new Part("diagnosis_7d",1,7,anchor,anchor.plusDays(7),1L,null);
        var node=new Node("node","r",1,anchor.plusDays(7),List.of(part),List.of(1L));
        var entry=new MediaAccountProfileEntryDO();entry.setAccountId(10L);
        entry.setContent(JsonUtils.toJsonString(Map.of("weeklyNode",node)));
        when(accounts.selectById(10L)).thenReturn(account);when(entries.selectById(101L)).thenReturn(entry);
        when(metrics.read(any(),any(),any(),any(),isNull(),eq(false),any(DiagnosisMetricService.ReadContext.class))).thenReturn(new Metric());
        var result=service.history(10L,101L,null,20L);
        assertEquals("node",result.node().key());assertEquals(1,result.node().parts().size());
        verifyNoInteractions(profiles,reminders,tasks,commands,dicts);
        verify(accounts,times(1)).selectById(10L);
    }
    @Test void historyRejectsMissingAccountAndForeignEntryBeforeMetrics(){
        assertThrows(RuntimeException.class,()->service.history(10L,101L,null,20L));
        verifyNoInteractions(entries,metrics,profiles);
        when(accounts.selectById(10L)).thenReturn(account);
        var foreign=new MediaAccountProfileEntryDO();foreign.setAccountId(11L);
        when(entries.selectById(101L)).thenReturn(foreign);
        assertThrows(RuntimeException.class,()->service.history(10L,101L,null,20L));
        verifyNoInteractions(metrics,profiles);
    }
}
