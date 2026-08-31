package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedFilterServiceTest {
    @InjectMocks private AdvancedFilterService service;
    @Mock private AdvancedFilterMapper mapper;
    @BeforeEach void setUp() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void composesGroupsAndKeepsTenantParameter() {
        AdvancedFilterGroupReqVO root = group("AND", condition("person.name", "contains", "张三"));
        root.getGroups().add(group("OR", condition("order.totalAmount", "gt", "100"), condition("order.status", "in", List.of("effective"))));
        when(mapper.selectLeadIds(any())).thenReturn(List.of(1L));
        assertEquals(List.of(1L), service.matchLeadIds(root));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        assertEquals(7L, captor.getValue().getParameters().get("tenantId"));
        assertTrue(captor.getValue().getWhereSql().contains("p.name LIKE"));
        assertTrue(captor.getValue().getWhereSql().contains("EXISTS (SELECT 1 FROM zsjos_order ro"));
        assertTrue(captor.getValue().getWhereSql().contains(" OR "));
    }

    @Test void usesNotExistsForNegativeRelatedCondition() {
        when(mapper.selectLeadIds(any())).thenReturn(List.of());
        service.matchLeadIds(group("AND", condition("opportunity.status", "not_in", List.of("lost"))));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        String sql = captor.getValue().getWhereSql();
        assertTrue(sql.startsWith("NOT EXISTS (SELECT 1 FROM zsjos_opportunity"));
        assertTrue(sql.contains("op.status IN ("));
        assertFalse(sql.contains("NOT (op.status IN"));
    }

    @Test void relationNegativeOperatorsUsePositiveInnerPredicates() {
        when(mapper.selectLeadIds(any())).thenReturn(List.of());
        service.matchLeadIds(group("AND", condition("order.orderNo", "not_contains", "legacy")));
        service.matchLeadIds(group("AND", condition("order.orderNo", "ne", "SO-1")));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper, times(2)).selectLeadIds(captor.capture());
        assertTrue(captor.getAllValues().get(0).getWhereSql().contains("NOT EXISTS"));
        assertTrue(captor.getAllValues().get(0).getWhereSql().contains("ro.order_no LIKE"));
        assertFalse(captor.getAllValues().get(0).getWhereSql().contains("NOT (ro.order_no LIKE"));
        assertTrue(captor.getAllValues().get(1).getWhereSql().contains("ro.order_no ="));
        assertFalse(captor.getAllValues().get(1).getWhereSql().contains("NOT (ro.order_no ="));
    }

    @Test void acceptsEpochAndOffsetDatesAndNormalizesThemForMySql() {
        when(mapper.selectOrderIds(any())).thenReturn(List.of());
        service.matchOrderIds(group("AND", condition("order.submittedAt", "eq", 1786608000000L)));
        service.matchOrderIds(group("AND", condition("order.submittedAt", "eq", "2026-08-13T08:00:00.000Z")));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper, times(2)).selectOrderIds(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(query -> query.getParameters().values().stream().anyMatch(LocalDateTime.class::isInstance)));
    }

    @Test void rejectsInvalidContracts() {
        assertThrows(ServiceException.class, () -> service.matchLeadIds(group("AND", condition("l.status", "eq", "x"))));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(group("AND", condition("lead.name", "gt", "x"))));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(group("AND", condition("order.totalAmount", "gt", "abc"))));
        assertThrows(ServiceException.class, () -> service.matchOrderIds(group("AND", condition("order.submittedAt", "eq", "999999999999999999999999"))));
        AdvancedFilterConditionReqVO range = condition("order.totalAmount", "between", null); range.setValueFrom("1");
        assertThrows(ServiceException.class, () -> service.matchLeadIds(group("AND", range)));
        AdvancedFilterConditionReqVO reversed = condition("order.totalAmount", "between", null);
        reversed.setValueFrom("2"); reversed.setValueTo("1");
        assertThrows(ServiceException.class, () -> service.matchLeadIds(group("AND", reversed)));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(
                group("AND", condition("order.status", "in", List.of("effective", " ")))));
        AdvancedFilterGroupReqVO oversized = group("AND"); for (int i = 0; i < 21; i++) oversized.getConditions().add(condition("lead.name", "contains", "x"));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(oversized));
    }

    @Test void rejectsThirdLevelAndTooManyChildGroups() {
        AdvancedFilterGroupReqVO root = group("AND"), child = group("AND"); child.getGroups().add(group("AND", condition("lead.name", "contains", "x"))); root.getGroups().add(child);
        assertThrows(ServiceException.class, () -> service.matchLeadIds(root));
        AdvancedFilterGroupReqVO six = group("AND"); for (int i = 0; i < 6; i++) six.getGroups().add(group("AND", condition("lead.name", "contains", "x")));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(six));
        AdvancedFilterGroupReqVO emptyChild = group("AND"); emptyChild.getGroups().add(group("OR"));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(emptyChild));
    }

    @Test void rejectsNullCollectionsAndElementsDefensively() {
        AdvancedFilterGroupReqVO nullConditions = group("AND");
        nullConditions.setConditions(null);
        assertThrows(ServiceException.class, () -> service.matchLeadIds(nullConditions));

        AdvancedFilterGroupReqVO nullGroups = group("AND");
        nullGroups.setGroups(null);
        assertThrows(ServiceException.class, () -> service.matchLeadIds(nullGroups));

        AdvancedFilterGroupReqVO nullCondition = group("AND");
        nullCondition.getConditions().add(null);
        assertThrows(ServiceException.class, () -> service.matchLeadIds(nullCondition));

        AdvancedFilterGroupReqVO nullGroup = group("AND");
        nullGroup.getGroups().add(null);
        assertThrows(ServiceException.class, () -> service.matchLeadIds(nullGroup));
    }

    @Test void catalogContainsControlledOptionsAndRejectsUnknownScene() {
        var catalog = service.catalog("lead");
        assertTrue(catalog.fields().stream().allMatch(field -> field.fieldKey().contains(".")));
        assertFalse(catalog.fields().stream().filter(field -> field.fieldKey().equals("lead.status")).findFirst().orElseThrow().options().isEmpty());
        var duration = catalog.fields().stream().filter(field -> field.fieldKey().equals("duration.diff")).findFirst().orElseThrow();
        assertEquals("时间作差", duration.label());
        assertEquals("时间", duration.group());
        assertEquals("duration", duration.valueType());
        assertEquals(List.of("gt", "gte", "lt", "lte", "between"), duration.operators());
        assertTrue(duration.options().size() >= 2);
        assertTrue(duration.options().stream().anyMatch(option -> option.value().equals("lead.submittedAt")));
        assertTrue(duration.options().stream().allMatch(option -> catalog.fields().stream()
                .anyMatch(field -> field.fieldKey().equals(option.value()) && field.valueType().equals("date"))));
        assertTrue(catalog.fields().stream().anyMatch(field -> field.fieldKey().equals("lead.leadNo") && field.label().equals("客资编号")));
        assertTrue(catalog.fields().stream().noneMatch(field -> Set.of("lead.id", "lead.leadId", "person.id").contains(field.fieldKey())));
        assertTrue(catalog.fields().stream().allMatch(field -> Set.of("身份与联系", "状态与进度", "归属与人员", "产品与服务", "金额与付款", "时间", "补充信息", "业务指标").contains(field.group())));
        assertEquals(8, catalog.relativeDateOptions().size());
        for (String scene : List.of("order", "lead_appeal", "duplicate_review", "registration", "student", "subordinate_sales")) {
            assertFalse(service.catalog(scene).fields().isEmpty(), scene);
        }
        assertThrows(ServiceException.class, () -> service.catalog("audit"));
    }

    @Test void catalogHydratesVisibleUsersWithoutLeavingAnUnscopedOptionSource() {
        var users = List.of(new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO.OptionVO("12", "可见销售"));
        var catalog = service.catalog("order", users);
        var personnelFields = catalog.fields().stream()
                .filter(field -> field.group().equals("归属与人员"))
                .toList();
        assertFalse(personnelFields.isEmpty());
        assertTrue(personnelFields.stream().allMatch(field -> field.optionSource() == null));
        assertTrue(personnelFields.stream().allMatch(field -> field.options().equals(users)));
        assertTrue(service.catalog("order", null).fields().stream()
                .noneMatch(field -> "visible-users".equals(field.optionSource())
                        || field.group().equals("归属与人员")));
        assertTrue(service.catalogWithoutVisibleUsers("registration").fields().stream()
                .noneMatch(field -> "visible-users".equals(field.optionSource())));
    }

    @Test void compilesDurationDiffInMinutesWithNonNullGuards() {
        when(mapper.selectOrderIds(any())).thenReturn(List.of());
        service.matchOrderIds(group("AND", duration("order.submittedAt", "order.effectiveAt", "gte", "hour", 24)));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectOrderIds(captor.capture());
        String sql = captor.getValue().getWhereSql();
        assertTrue(sql.contains("o.submitted_at IS NOT NULL"));
        assertTrue(sql.contains("o.effective_at IS NOT NULL"));
        assertTrue(sql.contains("TIMESTAMPDIFF(MINUTE, o.submitted_at, o.effective_at) >="));
        assertTrue(captor.getValue().getParameters().containsValue(new java.math.BigDecimal("1440")));
    }

    @Test void durationDiffAllowsRootAndOneRelatedRelation() {
        when(mapper.selectLeadIds(any())).thenReturn(List.of());
        service.matchLeadIds(group("AND", duration("lead.submittedAt", "order.effectiveAt", "lt", "day", 2)));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        String sql = captor.getValue().getWhereSql();
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM zsjos_order ro"));
        assertTrue(sql.contains("l.submitted_at IS NOT NULL"));
        assertTrue(sql.contains("ro.effective_at IS NOT NULL"));
        assertTrue(sql.contains("TIMESTAMPDIFF(MINUTE, l.submitted_at, ro.effective_at) <"));
        assertTrue(captor.getValue().getParameters().containsValue(new java.math.BigDecimal("2880")));
    }

    @Test void durationDiffAllowsTwoFieldsFromTheSameRelation() {
        when(mapper.selectLeadIds(any())).thenReturn(List.of());
        service.matchLeadIds(group("AND", duration("order.submittedAt", "order.effectiveAt", "gte", "minute", 30)));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        String sql = captor.getValue().getWhereSql();
        String relation = "EXISTS (SELECT 1 FROM zsjos_order ro";
        assertEquals(1, (sql.length() - sql.replace(relation, "").length()) / relation.length());
        assertTrue(sql.contains("TIMESTAMPDIFF(MINUTE, ro.submitted_at, ro.effective_at) >="));
    }

    @Test void durationDiffRejectsFieldsFromTwoDifferentRelations() {
        assertThrows(ServiceException.class, () -> service.matchRegistrationCaseIds(group("AND",
                duration("lead.submittedAt", "order.submittedAt", "gte", "hour", 1))));
    }

    @Test void durationDiffBetweenValidatesThresholdOrderAndScalesUnits() {
        when(mapper.selectOrderIds(any())).thenReturn(List.of());
        AdvancedFilterConditionReqVO between = duration("order.submittedAt", "order.effectiveAt", "between", "hour", null);
        between.setValueFrom("1.5");
        between.setValueTo("2");
        service.matchOrderIds(group("AND", between));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectOrderIds(captor.capture());
        assertTrue(captor.getValue().getWhereSql().contains(" BETWEEN "));
        assertTrue(captor.getValue().getParameters().containsValue(new java.math.BigDecimal("90.0")));
        assertTrue(captor.getValue().getParameters().containsValue(new java.math.BigDecimal("120")));

        AdvancedFilterConditionReqVO reversed = duration("order.submittedAt", "order.effectiveAt", "between", "minute", null);
        reversed.setValueFrom("10");
        reversed.setValueTo("1");
        assertThrows(ServiceException.class, () -> service.matchOrderIds(group("AND", reversed)));
    }

    @Test void durationDiffRejectsUnknownOrNonDateOperands() {
        assertThrows(ServiceException.class, () -> service.matchOrderIds(group("AND",
                duration("order.totalAmount", "order.effectiveAt", "gte", "hour", 1))));
        assertThrows(ServiceException.class, () -> service.matchOrderIds(group("AND",
                duration("order.submittedAt", "order.effectiveAt", "gte", "week", 1))));
        assertThrows(ServiceException.class, () -> service.matchOrderIds(group("AND",
                duration("order.submittedAt", "order.unknownAt", "gte", "hour", 1))));
        assertThrows(ServiceException.class, () -> service.matchOrderIds(group("AND",
                duration("order.submittedAt", "order.effectiveAt", "eq", "hour", 1))));
    }

    @Test void mergesPositiveAndConditionsForTheSameOrderRelation() {
        when(mapper.selectLeadIds(any())).thenReturn(List.of());
        service.matchLeadIds(group("AND",
                condition("order.orderNo", "contains", "SO"),
                condition("order.totalAmount", "gte", "1000")));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        String sql = captor.getValue().getWhereSql();
        String relation = "EXISTS (SELECT 1 FROM zsjos_order ro";
        assertEquals(1, (sql.length() - sql.replace(relation, "").length()) / relation.length());
        assertTrue(sql.contains("ro.order_no LIKE") && sql.contains("ro.total_amount >=") && sql.contains(" AND "));
    }

    @Test void resolvesRelativeDatesInBeijingNaturalDays() {
        when(mapper.selectOrderIds(any())).thenReturn(List.of());
        service.matchOrderIds(group("AND", condition("order.submittedAt", "relative", "last_7_days")));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectOrderIds(captor.capture());
        List<LocalDateTime> dates = captor.getValue().getParameters().values().stream()
                .filter(LocalDateTime.class::isInstance).map(LocalDateTime.class::cast).sorted().toList();
        assertEquals(2, dates.size());
        assertEquals(7, java.time.Duration.between(dates.get(0), dates.get(1)).toDays());
        assertThrows(ServiceException.class, () -> service.matchOrderIds(
                group("AND", condition("order.submittedAt", "relative", "last_365_days"))));
    }

    @Test void evaluatesSubordinateMetricsAfterAuthorizationScopeIsBuilt() {
        AdvancedFilterGroupReqVO filter = group("AND",
                condition("subordinate.accountStatus", "in", List.of("0")),
                condition("subordinate.effectiveOrderAmount", "gte", "1000"));
        assertTrue(service.matches("subordinate_sales", filter, key -> switch (key) {
            case "subordinate.accountStatus" -> 0;
            case "subordinate.effectiveOrderAmount" -> 1500;
            default -> null;
        }));
        assertFalse(service.matches("subordinate_sales", filter, key -> switch (key) {
            case "subordinate.accountStatus" -> 0;
            case "subordinate.effectiveOrderAmount" -> 999;
            default -> null;
        }));
    }

    @Test void inMemoryEvaluationValidatesOperandsBeforeReadingActualValues() {
        assertThrows(ServiceException.class, () -> service.matches("subordinate_sales",
                group("AND", condition("subordinate.name", "contains", " ")), ignored -> null));
        AdvancedFilterConditionReqVO reversed = condition("subordinate.effectiveOrderAmount", "between", null);
        reversed.setValueFrom("10"); reversed.setValueTo("1");
        assertThrows(ServiceException.class, () -> service.matches("subordinate_sales",
                group("AND", reversed), ignored -> null));
    }

    @Test void studentSqlRequiresSelectedRouteActiveServiceAndTenantEquality() {
        String sql = AdvancedFilterMapper.SqlProvider.studentSql(java.util.Map.of());
        assertTrue(sql.contains("vcr.selected=b'1'"));
        assertTrue(sql.contains("vsr.status='active'"));
        assertTrue(sql.contains("vcr.tenant_id=vsr.tenant_id"));
        assertTrue(sql.contains("vsr.tenant_id=p.tenant_id"));
    }

    private static AdvancedFilterGroupReqVO group(String logic, AdvancedFilterConditionReqVO... conditions) { AdvancedFilterGroupReqVO value = new AdvancedFilterGroupReqVO(); value.setLogic(logic); value.getConditions().addAll(List.of(conditions)); return value; }
    private static AdvancedFilterConditionReqVO condition(String field, String operator, Object value) { AdvancedFilterConditionReqVO result = new AdvancedFilterConditionReqVO(); result.setFieldKey(field); result.setOperator(operator); result.setValue(value); return result; }
    private static AdvancedFilterConditionReqVO duration(String startField, String endField, String operator, String unit, Object value) {
        AdvancedFilterConditionReqVO result = condition("duration.diff", operator, value);
        result.setStartFieldKey(startField);
        result.setEndFieldKey(endField);
        result.setUnit(unit);
        return result;
    }
}
