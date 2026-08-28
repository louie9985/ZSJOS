package cn.iocoder.yudao.module.bpm.service.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmProcessInstanceRelationServiceImplTest {

    @InjectMocks private BpmProcessInstanceRelationServiceImpl service;
    @Mock private HistoryService historyService;
    @Mock private HistoricProcessInstanceQuery historyQuery;
    @Mock private BpmProcessDefinitionService processDefinitionService;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void findsRelationFieldsRecursivelyAndKeepsSchemaOrder() {
        List<String> fields = List.of(
                "{\"type\":\"group\",\"children\":[{\"type\":\"ProcessInstanceSelect\",\"field\":\"relatedApprovals\"}]}",
                "{\"type\":\"ProcessInstanceSelect\",\"field\":\"secondaryApprovals\"}",
                "{\"type\":\"input\",\"field\":\"title\"}");

        assertEquals(List.of("relatedApprovals", "secondaryApprovals"),
                service.getRelationFields(fields));
    }

    @Test
    void ignoresMalformedRelationNodesWithoutField() {
        assertEquals(List.of(), service.getRelationFields(List.of(
                "{\"type\":\"ProcessInstanceSelect\"}",
                "{\"type\":\"input\",\"field\":\"relatedApprovals\"}")));
    }

    @Test
    void rejectsNonArrayAndMoreThanTwentyValuesBeforeStartingFlowable() {
        var info = new cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO()
                .setFormFields(List.of("{\"type\":\"ProcessInstanceSelect\",\"field\":\"related\"}"));
        Map<String, Object> invalid = Map.of("related", "one-id");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.prepare(1L, null, info, invalid));

        Map<String, Object> tooMany = new HashMap<>();
        tooMany.put("related", new ArrayList<>(java.util.stream.IntStream.range(0, 21)
                .mapToObj(String::valueOf).toList()));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.prepare(1L, null, info, tooMany));
    }

    @Test
    void rejectsDuplicateTargetIds() {
        var info = new cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO()
                .setFormFields(List.of("{\"type\":\"ProcessInstanceSelect\",\"field\":\"related\"}"));
        HistoricProcessInstance target = org.mockito.Mockito.mock(HistoricProcessInstance.class);
        when(target.getStartUserId()).thenReturn("1");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceTenantId(anyString())).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(anyString())).thenReturn(historyQuery);
        when(historyQuery.includeProcessVariables()).thenReturn(historyQuery);
        when(historyQuery.singleResult()).thenReturn(target);

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.prepare(1L, null, info, Map.of("related", List.of("target-1", "target-1"))));
    }
}
