package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeadFlowProjectionTest {
    @Test void snapshotNamesSurviveAccountDeletionAndRemainMasked() {
        var permissions=mock(LeadObjectPermissionService.class);
        var masking=new LeadIdentityMaskingService(permissions);
        var projection=new LeadFlowProjection();
        ReflectionTestUtils.setField(projection,"masking",masking);
        ReflectionTestUtils.setField(projection,"leadPermission",permissions);
        LeadDO lead=new LeadDO(); lead.setId(1L); lead.setProviderOwnerType("system_user"); lead.setProviderOwnerId(10L);
        lead.setOwnerUserId(20L); lead.setAssignmentStatus("owned"); lead.setDispatchMode("auto");
        var snapshot=new LeadFlowSnapshot(1,"key","主管恢复客资","ADMIN",10L,"测试姓名","已恢复",null,"lead",1L,null,
                List.of(new LeadFlowSnapshot.Change("owner","负责人",new LeadFlowSnapshot.Value("VALUE","10","测试姓名","ADMIN",10L),LeadFlowSnapshot.Value.text(null))),List.of());
        BusinessEventDO event=new BusinessEventDO(); event.setId(9L); event.setEventType("lead_flow_operation");
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("flowSnapshot",snapshot)));
        var read=LeadFlowProjection.snapshot(event);
        assertEquals("测试姓名",read.actorName());
        var hidden=projection.project(event,read,lead,20L,masking.resolve(20L,lead));
        assertNotEquals("测试姓名",hidden.getOperator());
        assertEquals(hidden.getOperator(),hidden.getChanges().getFirst().before().text());
        when(permissions.canViewUnmaskedIdentity(99L,lead)).thenReturn(true);
        assertEquals("测试姓名",projection.project(event,read,lead,99L,masking.resolve(99L,lead)).getOperator());
    }
    @Test void relatedPermissionDoesNotLeakReasonOrReferencesButKeepsLeadChanges() {
        var permissions=mock(LeadObjectPermissionService.class);
        var projection=new LeadFlowProjection(); var masking=new LeadIdentityMaskingService(permissions);
        ReflectionTestUtils.setField(projection,"masking",masking);
        ReflectionTestUtils.setField(projection,"leadPermission",permissions);
        ReflectionTestUtils.setField(projection,"security",mock(SecurityFrameworkService.class));
        var snapshot=new LeadFlowSnapshot(1,"key","投诉处理结果","SYSTEM",null,"系统","投诉成立","受限意见",
                "complaint",55L,null,List.of(new LeadFlowSnapshot.Change("status","客资状态",LeadFlowSnapshot.Value.text("valid"),LeadFlowSnapshot.Value.text("invalid"))),List.of());
        var lead=new LeadDO(); lead.setId(1L);
        var event=new BusinessEventDO();event.setId(1L);
        var result=projection.project(event,snapshot,lead,20L,masking.resolve(20L,lead));
        assertNull(result.getReason()); assertNull(result.getRelatedId());
        assertEquals("投诉成立",result.getOperationResult());
        assertEquals(1,result.getChanges().size());
    }
}
