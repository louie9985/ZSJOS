package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagnosisMetricServiceTest {
    @InjectMocks DiagnosisMetricService service;
    @Mock DiagnosisMetricMapper metrics;
    @Mock PartnerStudentLinkMapper links;
    @Mock PartnerMapper partners;
    @Mock MediaAccountProfileEntryMapper entries;
    MediaAccountDO account;
    @BeforeAll static void metadata(){
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(config,"diagnosis-metrics-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,DiagnosisMetricDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,MediaAccountProfileEntryDO.class);
    }
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);account=new MediaAccountDO().setId(10L).setStudentPersonId(20L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    Part part(int days){var end=DiagnosisMetricService.now().minusDays(1);return new Part("diagnosis_"+days+"d",1,days,end.minusDays(days),end,1L,null);}
    @Test void noBindingDoesNotBecomeZero(){
        when(metrics.selectList(any())).thenReturn(List.of());
        var result=service.read(account,"node","round",part(14),null,false);
        assertEquals("UNAVAILABLE",result.getState());assertNull(result.getValidLeads());assertNull(result.getDeals());
        assertTrue(result.getReason().contains("未绑定"));
    }
    @Test void settledCohortNeverRebindsButDealsRefresh(){
        var frozen=new Metric();frozen.setState("SETTLED");frozen.setValidLeads(4L);frozen.setDeals(1L);
        var row=new DiagnosisMetricDO();row.setId(99L);row.setState("SETTLED");row.setMetricJson(JsonUtils.toJsonString(frozen));
        when(metrics.selectList(any())).thenReturn(List.of(row));when(metrics.deals(eq(1L),eq(99L),any())).thenReturn(2L);
        var result=service.read(account,"node","round",part(14),null,false);
        assertEquals(4L,result.getValidLeads());assertEquals(2L,result.getDeals());
        assertEquals(new java.math.BigDecimal("0.5000"),result.getConversionRate());verifyNoInteractions(links,partners);
    }
    @Test void missingOneHistoricalStatusInvalidatesTheWholeTotal(){
        when(metrics.selectList(any())).thenReturn(List.of());
        var link=new PartnerStudentLinkDO();link.setPartnerId(30L);link.setId(31L);
        when(links.selectActiveByStudent(20L)).thenReturn(link);when(partners.selectById(30L)).thenReturn(new PartnerDO());
        when(metrics.cohort(eq(1L),eq(30L),any(),any())).thenReturn(List.of(Map.of("id",1L,"status","won"),Map.of("id",2L,"status","valid")));
        when(metrics.statusesBefore(eq(1L),anyList(),any())).thenReturn(List.of(Map.of("leadId",1L,"toStatus","valid")));
        when(metrics.flowsBefore(eq(1L),eq(List.of(2L)),any())).thenReturn(List.of());
        var result=service.read(account,"node","round",part(14),null,false);
        assertEquals("UNAVAILABLE",result.getState());assertNull(result.getValidLeads());assertNull(result.getConversionRate());
    }
    @Test void lateInvalidationDoesNotRemovePeriodEndValidLead(){
        when(metrics.selectList(any())).thenReturn(List.of());
        var link=new PartnerStudentLinkDO();link.setPartnerId(30L);link.setId(31L);
        when(links.selectActiveByStudent(20L)).thenReturn(link);when(partners.selectById(30L)).thenReturn(new PartnerDO());
        when(metrics.cohort(eq(1L),eq(30L),any(),any())).thenReturn(List.of(Map.of("id",1L,"status","invalid")));
        when(metrics.statusesBefore(eq(1L),anyList(),any())).thenReturn(List.of(Map.of("leadId",1L,"toStatus","valid")));when(metrics.dealtLeadIds(eq(1L),eq(List.of(1L)),any())).thenReturn(List.of(1L));
        var result=service.read(account,"node","round",part(14),null,false);
        assertEquals(1L,result.getValidLeads());assertEquals(1L,result.getDeals());
    }
    @Test void overlappingWindowsAndSaveReuseBatchEvidenceWithinOneRequest() {
        var link=new PartnerStudentLinkDO();link.setPartnerId(30L);link.setId(31L);
        when(links.selectActiveByStudent(20L)).thenReturn(link);when(partners.selectById(30L)).thenReturn(new PartnerDO());
        var end=DiagnosisMetricService.now().minusDays(1);
        var fourteen=new Part("diagnosis_14d",2,14,end.minusDays(14),end,1L,null);
        var twentyEight=new Part("diagnosis_28d",1,28,end.minusDays(28),end,2L,null);
        when(metrics.cohort(eq(1L),eq(30L),eq(fourteen.from()),any())).thenReturn(List.of(Map.of("id",1L)));
        when(metrics.cohort(eq(1L),eq(30L),eq(twentyEight.from()),any())).thenReturn(List.of(Map.of("id",1L),Map.of("id",2L)));
        when(metrics.statusesBefore(eq(1L),anyList(),any())).thenAnswer(call -> ((List<Long>)call.getArgument(1)).stream()
                .map(id -> Map.<String,Object>of("leadId",id,"toStatus","valid")).toList());
        when(metrics.dealtLeadIds(eq(1L),anyList(),any())).thenAnswer(call -> call.getArgument(1));
        var context=new DiagnosisMetricService.ReadContext();
        assertEquals(1L,service.read(account,"node","round",fourteen,null,false,context).getDeals());
        assertEquals(2L,service.read(account,"node","round",twentyEight,null,false,context).getDeals());
        assertEquals(1L,service.read(account,"node","round",fourteen,null,true,context).getDeals());
        assertEquals(2L,service.read(account,"node","round",twentyEight,null,true,context).getDeals());
        verify(metrics,times(2)).cohort(anyLong(),anyLong(),any(),any());
        verify(metrics,times(2)).statusesBefore(anyLong(),anyList(),any());
        verify(metrics,times(2)).dealtLeadIds(anyLong(),anyList(),any());
        verify(metrics,never()).statusBefore(anyLong(),anyLong(),any());
        verify(metrics,never()).leadDeals(anyLong(),anyLong(),any());
    }

    @Test void publicationWindowsAndLikesReuseEvidenceWithoutChangingResults(){
        var end=DiagnosisMetricService.now().minusDays(1);
        var seven=new Part("diagnosis_7d",4,7,end.minusDays(7),end,1L,null);
        var twentyEight=new Part("diagnosis_28d",1,28,end.minusDays(28),end,2L,null);
        when(metrics.publicationPeriods(eq(1L),eq(10L),eq(seven.from()),eq(twentyEight.from()),eq(end),eq(end)))
                .thenReturn(Map.of("published7",3L,"published28",9L,"missing",2L));
        var rows=new ArrayList<MediaAccountProfileEntryDO>();
        for(String json:List.of("{\"roundKey\":\"round\",\"week\":1,\"highestLikes\":8}",
                "{\"roundKey\":\"round\",\"week\":1,\"highestLikes\":2}",
                "{\"roundKey\":\"round\",\"week\":2,\"highestLikes\":5}",
                "{\"roundKey\":\"old\",\"week\":3,\"highestLikes\":999}",
                "{\"roundKey\":\"round\",\"week\":5,\"highestLikes\":999}")){
            var row=new MediaAccountProfileEntryDO();row.setContent(json);rows.add(row);
        }
        when(entries.selectList(any())).thenReturn(rows);
        var context=new DiagnosisMetricService.ReadContext();
        var first=service.read(account,"node","round",seven,0L,false,context);
        var preview=service.read(account,"node","round",twentyEight,0L,false,context);
        var saved=service.read(account,"node","round",twentyEight,0L,true,context);
        assertEquals(3L,first.getPublishedCount());assertEquals(9L,preview.getPublishedCount());
        assertEquals(2L,preview.getMissingPublishTimeCount());assertEquals(3,preview.getLikesCoverage());
        assertEquals(5L,preview.getHighestLikes());
        assertEquals(JsonUtils.toJsonString(preview),JsonUtils.toJsonString(saved));
        verify(metrics,times(1)).publicationPeriods(anyLong(),anyLong(),any(),any(),any(),any());
        verify(entries,times(1)).selectList(any());
        verify(metrics,never()).publications(anyLong(),anyLong(),any(),any(),any());
        // A new request must observe current publications and revisions rather than a process-wide cache.
        service.read(account,"node","round",twentyEight,0L,false);
        verify(metrics,times(2)).publicationPeriods(anyLong(),anyLong(),any(),any(),any(),any());
        verify(entries,times(2)).selectList(any());
    }
    @Test void nonstandardHistoricalBoundsKeepOriginalPublicationQuery(){
        var end=DiagnosisMetricService.now().minusDays(1);
        var historical=new Part("diagnosis_7d",1,7,end.minusDays(6),end,1L,null);
        when(metrics.publications(1L,10L,historical.from(),end,end)).thenReturn(Map.of("published",4L,"missing",1L));
        var result=service.read(account,"node","round",historical,null,false);
        assertEquals(4L,result.getPublishedCount());assertEquals(1L,result.getMissingPublishTimeCount());
        verify(metrics,never()).publicationPeriods(anyLong(),anyLong(),any(),any(),any(),any());
    }
}
