package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.homemetric.source.MetricSourceDeadline;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeMetricPageServiceTest {
    @InjectMocks HomeMetricPageService service;
    @Mock HomeMetricConfigService contexts;
    @Mock LeadManagementService leads;
    @Mock Validator validator;
    @Mock MetricSourceDeadline deadline;
    final AdvancedFilterService filters = new AdvancedFilterService();
    final MetricPeriod.Range range = new MetricPeriod.Range(LocalDateTime.of(2026,11,1,0,0),LocalDateTime.of(2026,11,2,0,0));
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    AdvancedFilterGroupReqVO condition(String value) {
        var c=new AdvancedFilterConditionReqVO();c.setFieldKey("lead.name");c.setOperator("contains");c.setValue(value);
        var g=new AdvancedFilterGroupReqVO();g.setConditions(List.of(c));return g;
    }
    HomeMetricSpec.Operand operand(AdvancedFilterGroupReqVO group) {
        return new HomeMetricSpec.Operand(new MetricEvaluation.Operand("A","lead",MetricAggregate.DISTINCT_COUNT,null,MetricPeriod.Kind.DAY,false,null,MetricPeriod.Comparison.CURRENT),group,"submittedAt");
    }
    HomeMetricConfigService.Context context() {
        var spec=new HomeMetricSpec("test","test","","",0,"test:view",List.of(),List.of(),"MODAL","A",false,0,List.of(operand(condition("fixed"))));
        var value=new HomeMetricConfigService.Value("OK","1");
        return new HomeMetricConfigService.Context(1L,1,7L,1L,spec,new HomeMetricConfigService.Evaluation(Instant.now(),value,MetricUnit.of("COUNT"),List.of(new HomeMetricConfigService.Input("A","lead",range,value))),Instant.now().plusSeconds(60));
    }
    @Test void preservesNestedOrAndFrozenRangeWithoutMutatingConfiguration() {
        var original=condition("fixed");original.setLogic("OR");original.setGroups(List.of(condition("alternative")));
        var bounded=HomeMetricPageService.bounded(operand(original),range,condition("extra"));
        var sql=filters.buildLeadQuery(bounded);
        assertTrue(sql.getWhereSql().contains(" OR "));assertTrue(sql.getWhereSql().contains(" AND "));
        assertTrue(sql.getWhereSql().contains("submitted_at >="));assertTrue(sql.getWhereSql().contains("submitted_at <"));
        assertTrue(sql.getParameters().containsValue("fixed"));assertTrue(sql.getParameters().containsValue("extra"));
        assertEquals(1,original.getConditions().size());assertEquals("OR",original.getLogic());
        // JSON cannot create the trusted server intersection subtype.
        var untrusted=JsonUtils.parseObject(JsonUtils.toJsonString(bounded),AdvancedFilterGroupReqVO.class);
        assertThrows(RuntimeException.class,()->filters.buildLeadQuery(untrusted));
    }
    @Test void emptyUserFilterDoesNotProduceEmptySqlParentheses() {
        var sql=filters.buildLeadQuery(HomeMetricPageService.bounded(operand(new AdvancedFilterGroupReqVO()),range,new AdvancedFilterGroupReqVO()));
        assertFalse(sql.getWhereSql().contains("()"));assertTrue(sql.getParameters().containsValue(range.start()));
    }
    @Test void clearingPageFilterRetainsFrozenPopulationAndNativePageProjection() {
        when(contexts.context("token",7L)).thenReturn(context());
        when(validator.validate(any())).thenReturn(Set.of());
        when(deadline.execute(any(),any())).thenAnswer(i -> ((Supplier<?>)i.getArgument(1)).get());
        when(leads.getLeadPage(any(),eq(7L))).thenReturn(PageResult.empty());
        service.page(new HomeMetricPageService.Request("token","A","lead",false,Map.of("pageNo",2,"pageSize",20)),7L);
        var capture=ArgumentCaptor.forClass(LeadManagementPageReqVO.class);verify(leads).getLeadPage(capture.capture(),eq(7L));
        assertEquals(2,capture.getValue().getPageNo());
        assertTrue(filters.buildLeadQuery(capture.getValue().getAdvancedFilter()).getParameters().containsValue("fixed"));
        verify(contexts,times(2)).context("token",7L);
    }
    @Test void invalidContextNeverQueriesBusinessRows() {
        when(contexts.context("expired",7L)).thenThrow(new IllegalArgumentException("expired"));
        assertThrows(RuntimeException.class,()->service.page(new HomeMetricPageService.Request("expired","A","lead",false,Map.of()),7L));
        verifyNoInteractions(leads);
    }
    @Test void cannotChangeSourceOrOperand() {
        when(contexts.context("token",7L)).thenReturn(context());
        assertThrows(RuntimeException.class,()->service.page(new HomeMetricPageService.Request("token","A","order",false,Map.of()),7L));
        assertThrows(RuntimeException.class,()->service.page(new HomeMetricPageService.Request("token","B","lead",false,Map.of()),7L));
        verifyNoInteractions(leads);
    }
    @Test void oversizedPageFailsClosed() {
        when(contexts.context("token",7L)).thenReturn(context());
        assertThrows(RuntimeException.class,()->service.page(new HomeMetricPageService.Request("token","A","lead",false,Map.of("pageSize",101)),7L));
        verifyNoInteractions(leads);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "lead,lead,submittedAt", "order,order,submittedAt", "order,order,effectiveAt",
        "student,student_service,activatedAt", "cashback,cashback,generatedAt",
        "cashback,cashback,availableAt", "cashback,cashback,settledAt",
        "withdrawal,withdrawal,submittedAt", "withdrawal,withdrawal,reviewedAt", "withdrawal,withdrawal,paidAt"
    })
    void mapsEverySqlSourceTimeFieldToItsOwningBusinessFilter(String source,String scene,String timeField) {
        var op=new HomeMetricSpec.Operand(new MetricEvaluation.Operand("A",source,MetricAggregate.COUNT,null,MetricPeriod.Kind.DAY,false,null,MetricPeriod.Comparison.CURRENT),new AdvancedFilterGroupReqVO(),timeField);
        assertDoesNotThrow(()->filters.validate(scene,HomeMetricPageService.bounded(op,range,null)));
    }
}
