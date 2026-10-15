package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeadFlowRecorderTest {
    @Test void recordsPersistedClearKeepsOldEventAndIgnoresReplay() {
        var recorder=new LeadFlowRecorder();
        var leads=mock(LeadMapper.class); var events=mock(BusinessEventMapper.class);
        var products=mock(LeadIntendedProductMapper.class);
        var cycles=mock(LeadAgingPoolCycleMapper.class);
        var assignments=mock(LeadAssignmentHistoryMapper.class);
        var aging=mock(LeadAgingPoolEventMapper.class);
        var evidence=mock(LeadFlowEvidence.class);
        ReflectionTestUtils.setField(recorder,"leadMapper",leads);
        ReflectionTestUtils.setField(recorder,"eventMapper",events);
        ReflectionTestUtils.setField(recorder,"productMapper",products);
        ReflectionTestUtils.setField(recorder,"cycleMapper",cycles);
        ReflectionTestUtils.setField(recorder,"assignmentMapper",assignments);
        ReflectionTestUtils.setField(recorder,"agingMapper",aging);
        ReflectionTestUtils.setField(recorder,"evidenceService",evidence);
        when(evidence.capture(anyLong(),anyString(),anyLong(),anyString(),anyCollection(),anyBoolean()))
                .thenReturn(new LeadFlowEvidence.Evidence(null,List.of()));
        var old=new BusinessEventDO(); old.setId(9L); old.setEventType("lead_suspended");
        when(events.selectByLeadId(1L)).thenReturn(List.of(old));
        LeadDO lead=new LeadDO();lead.setId(1L);lead.setStatus("suspended");lead.setSuspendedAt(LocalDateTime.of(2026,10,1,10,0));
        when(leads.selectByIdForUpdate(1L,1L)).thenReturn(lead);
        TenantContextHolder.setTenantId(1L);
        try {
            var before=recorder.capture(1L);
            lead.setStatus("submitted");lead.setSuspendedAt(null);
            recorder.record(1L,before,"主管恢复客资","SYSTEM",null,"lead",1L,null,"已恢复",null,false,"restore:1");
            var captured=ArgumentCaptor.forClass(BusinessEventDO.class);
            verify(events).insert(captured.capture());
            var snapshot=LeadFlowProjection.snapshot(captured.getValue());
            assertEquals("EMPTY",snapshot.changes().stream().filter(c -> c.field().equals("suspendedAt")).findFirst().orElseThrow().after().state());
            assertEquals(List.of("status","suspendedAt"),snapshot.changes().stream().map(LeadFlowSnapshot.Change::field).toList());
            assertFalse(snapshot.coveredRecords().contains("event:9"));
            recorder.record(1L,recorder.capture(1L),"主管恢复客资","SYSTEM",null,"lead",1L,null,"已恢复",null,false,null);
            verify(events,times(1)).insert(any(BusinessEventDO.class));
            verify(events,never()).updateById(any(BusinessEventDO.class));
            assertEquals("lead_suspended",old.getEventType());
        } finally { TenantContextHolder.clear(); }
    }
}
