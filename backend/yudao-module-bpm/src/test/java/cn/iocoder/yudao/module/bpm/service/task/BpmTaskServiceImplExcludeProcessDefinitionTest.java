package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 通用审批中心排除专属业务审批流程的解析逻辑。
 * 背景：成交订单、客资申诉、物料审核已接入专属审批页，不能再出现在通用审批中心。
 */
class BpmTaskServiceImplExcludeProcessDefinitionTest {

    private final BpmTaskServiceImpl service = new BpmTaskServiceImpl();
    private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RepositoryService.class);
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);
        TenantContextHolder.setTenantId(1L);
    }

    @Test // 未传排除参数时不做任何流程定义查询，保持既有行为
    void noExclusionKeysResolvesToEmptySet() {
        BpmTaskPageReqVO reqVO = new BpmTaskPageReqVO();

        Set<String> ids = resolve(reqVO);

        assertTrue(ids.isEmpty());
        verifyNoInteractions(repositoryService);
    }

    @Test // 同一个 key 的多个版本都要排除，否则旧实例仍会漏进列表
    void everyVersionOfAnExcludedKeyIsCollected() {
        BpmTaskPageReqVO reqVO = new BpmTaskPageReqVO();
        reqVO.setExcludeProcessDefinitionKeys("zsjos_sales_order_dual_approval");
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        List<ProcessDefinition> definitions = List.of(definition("def-v2"), definition("def-v1"));
        when(query.list()).thenReturn(definitions);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);

        Set<String> ids = resolve(reqVO);

        assertEquals(Set.of("def-v1", "def-v2"), ids);
    }

    @Test // 逗号分隔的多个 key 各自解析，空项与多余空格被跳过
    void commaSeparatedKeysAreTrimmedAndBlanksSkipped() {
        BpmTaskPageReqVO reqVO = new BpmTaskPageReqVO();
        reqVO.setExcludeProcessDefinitionKeys(" zsjos_sales_order_dual_approval , ,zsjos_lead_appeal_review ");
        ProcessDefinitionQuery salesQuery = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        ProcessDefinitionQuery appealQuery = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        ProcessDefinition salesDefinition = mock(ProcessDefinition.class);
        ProcessDefinition appealDefinition = mock(ProcessDefinition.class);
        when(salesDefinition.getId()).thenReturn("def-sales");
        when(appealDefinition.getId()).thenReturn("def-appeal");
        when(salesQuery.list()).thenReturn(List.of(salesDefinition));
        when(appealQuery.list()).thenReturn(List.of(appealDefinition));
        doReturn(salesQuery, appealQuery).when(repositoryService).createProcessDefinitionQuery();

        Set<String> ids = resolve(reqVO);

        assertEquals(Set.of("def-sales", "def-appeal"), ids);
        // 空项被跳过：两个有效 key 只应产生两次查询
        verify(repositoryService, times(2)).createProcessDefinitionQuery();
        verify(salesQuery).processDefinitionKey("zsjos_sales_order_dual_approval");
        verify(appealQuery).processDefinitionKey("zsjos_lead_appeal_review");
    }

    private Set<String> resolve(BpmTaskPageReqVO reqVO) {
        return ReflectionTestUtils.invokeMethod(service, "resolveExcludedProcessDefinitionIds", reqVO);
    }

    private ProcessDefinitionQuery queryReturning(String definitionId) {
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        when(query.list()).thenReturn(List.of(definition(definitionId)));
        return query;
    }

    private ProcessDefinition definition(String definitionId) {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn(definitionId);
        return definition;
    }
}
