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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedFilterServiceTest {
    @InjectMocks private AdvancedFilterService service;
    @Mock private AdvancedFilterMapper mapper;
    @BeforeEach void setUp() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void composesGroupsAndKeepsTenantParameter() {
        AdvancedFilterGroupReqVO root = group("AND", condition("lead.name", "contains", "张三"));
        root.getGroups().add(group("OR", condition("order.totalAmount", "gt", "100"), condition("order.status", "in", List.of("effective"))));
        when(mapper.selectLeadIds(any())).thenReturn(List.of(1L));
        assertEquals(List.of(1L), service.matchLeadIds(root));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        assertEquals(7L, captor.getValue().getParameters().get("tenantId"));
        assertTrue(captor.getValue().getWhereSql().contains("l.submitted_name LIKE"));
        assertTrue(captor.getValue().getWhereSql().contains("EXISTS (SELECT 1 FROM zsjos_order so"));
        assertTrue(captor.getValue().getWhereSql().contains(" OR "));
    }

    @Test void usesNotExistsForNegativeRelatedCondition() {
        when(mapper.selectLeadIds(any())).thenReturn(List.of());
        service.matchLeadIds(group("AND", condition("opportunity.status", "not_in", List.of("lost"))));
        ArgumentCaptor<AdvancedFilterQuery> captor = ArgumentCaptor.forClass(AdvancedFilterQuery.class);
        verify(mapper).selectLeadIds(captor.capture());
        assertTrue(captor.getValue().getWhereSql().startsWith("NOT EXISTS (SELECT 1 FROM zsjos_opportunity"));
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
        AdvancedFilterGroupReqVO oversized = group("AND"); for (int i = 0; i < 21; i++) oversized.getConditions().add(condition("lead.name", "contains", "x"));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(oversized));
    }

    @Test void rejectsThirdLevelAndTooManyChildGroups() {
        AdvancedFilterGroupReqVO root = group("AND"), child = group("AND"); child.getGroups().add(group("AND", condition("lead.name", "contains", "x"))); root.getGroups().add(child);
        assertThrows(ServiceException.class, () -> service.matchLeadIds(root));
        AdvancedFilterGroupReqVO six = group("AND"); for (int i = 0; i < 6; i++) six.getGroups().add(group("AND", condition("lead.name", "contains", "x")));
        assertThrows(ServiceException.class, () -> service.matchLeadIds(six));
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
        assertThrows(ServiceException.class, () -> service.catalog("audit"));
    }

    private static AdvancedFilterGroupReqVO group(String logic, AdvancedFilterConditionReqVO... conditions) { AdvancedFilterGroupReqVO value = new AdvancedFilterGroupReqVO(); value.setLogic(logic); value.getConditions().addAll(List.of(conditions)); return value; }
    private static AdvancedFilterConditionReqVO condition(String field, String operator, Object value) { AdvancedFilterConditionReqVO result = new AdvancedFilterConditionReqVO(); result.setFieldKey(field); result.setOperator(operator); result.setValue(value); return result; }
}
