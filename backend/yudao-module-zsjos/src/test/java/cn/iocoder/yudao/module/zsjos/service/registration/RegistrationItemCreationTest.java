package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationItemCreationTest {
    @InjectMocks RegistrationServiceImpl service;
    @Mock private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoTaskService studentInfoTaskService;
    @Mock private RegistrationCaseObjectPermissionProvider casePermission;
    @Mock RegistrationCaseMapper caseMapper;
    @Mock SalesOrderMapper orderMapper;
    @Mock SalesOrderItemMapper orderItemMapper;
    @Mock RegistrationChecklistTemplateMapper templateMapper;
    @Mock RegistrationChecklistTemplateItemMapper templateItemMapper;
    @Mock RegistrationRouteOptionMapper routeOptionMapper;
    @Mock RegistrationCaseChecklistItemMapper caseItemMapper;
    @Mock RegistrationCaseRouteMapper caseRouteMapper;
    @Mock RegistrationNotifyPublisher registrationNotifyPublisher;

    @Test void eachItemGetsIndependentTaskAndChecklistEvenWithSameProductAndReplay() {
        var order = new SalesOrderDO(); order.setId(10L);
        var first = new SalesOrderItemDO(); first.setId(101L); first.setProductId(50L);
        var second = new SalesOrderItemDO(); second.setId(102L); second.setProductId(50L);
        var template = new RegistrationChecklistTemplateDO(); template.setPublishedVersionId(20L);
        var item = new RegistrationChecklistTemplateItemDO(); item.setId(30L); item.setEnabled(true);
        item.setItemType("checkbox"); item.setItemKey("verify"); item.setTitle("核对报名资料");
        var cases = new ArrayList<RegistrationCaseDO>();
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(10L)).thenReturn(List.of(first, second));
        when(caseMapper.selectListByOrderId(10L)).thenAnswer(call -> new ArrayList<>(cases));
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateItemMapper.selectByVersionId(20L)).thenReturn(List.of(item));
        when(caseMapper.insert(any(RegistrationCaseDO.class))).thenAnswer(call -> {
            RegistrationCaseDO row = call.getArgument(0); row.setId(1000L + cases.size()); cases.add(row); return 1;
        });
        TenantContextHolder.setTenantId(1L);
        try {
            assertEquals(1000L, service.ensureCaseAfterRegistrationApproval(10L, LocalDateTime.now()));
            assertEquals(1000L, service.ensureCaseAfterRegistrationApproval(10L, LocalDateTime.now()));
        } finally { TenantContextHolder.clear(); }
        assertEquals(List.of(101L, 102L), cases.stream().map(RegistrationCaseDO::getOrderItemId).toList());
        var checks = ArgumentCaptor.forClass(RegistrationCaseChecklistItemDO.class);
        verify(caseItemMapper, times(2)).insert(checks.capture());
        assertEquals(List.of(1000L, 1001L), checks.getAllValues().stream().map(RegistrationCaseChecklistItemDO::getRegistrationCaseId).toList());
        verify(registrationNotifyPublisher, times(2)).publishTaskCreated(any(), eq(order));
    }

    @Test void newTaskSkipsRetiredStudyPlannerItemAndRouteFromStalePublishedTemplate() {
        var order = new SalesOrderDO(); order.setId(10L);
        var only = new SalesOrderItemDO(); only.setId(101L); only.setProductId(50L);
        var template = new RegistrationChecklistTemplateDO(); template.setPublishedVersionId(20L);
        var planner = new RegistrationChecklistTemplateItemDO(); planner.setId(29L); planner.setEnabled(true);
        planner.setItemType("study_planner"); planner.setItemKey("study_planner"); planner.setTitle("配置学习规划师");
        var check = new RegistrationChecklistTemplateItemDO(); check.setId(30L); check.setEnabled(true);
        check.setItemType("checkbox"); check.setItemKey("verify"); check.setTitle("核对报名资料");
        var plannerRoute = new RegistrationRouteOptionDO(); plannerRoute.setId(40L); plannerRoute.setEnabled(true);
        plannerRoute.setAssigneeType("study_planner");
        var directorRoute = new RegistrationRouteOptionDO(); directorRoute.setId(41L); directorRoute.setEnabled(true);
        directorRoute.setAssigneeType("content_director");
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(10L)).thenReturn(List.of(only));
        when(caseMapper.selectListByOrderId(10L)).thenReturn(List.of());
        when(templateMapper.selectCurrent()).thenReturn(template);
        when(templateItemMapper.selectByVersionId(20L)).thenReturn(List.of(planner, check));
        when(routeOptionMapper.selectByVersionId(20L)).thenReturn(List.of(plannerRoute, directorRoute));
        when(caseMapper.insert(any(RegistrationCaseDO.class))).thenAnswer(call -> {
            call.<RegistrationCaseDO>getArgument(0).setId(1000L); return 1;
        });
        TenantContextHolder.setTenantId(1L);
        try { assertEquals(1000L, service.ensureCaseAfterRegistrationApproval(10L, LocalDateTime.now())); }
        finally { TenantContextHolder.clear(); }
        var checks = ArgumentCaptor.forClass(RegistrationCaseChecklistItemDO.class);
        verify(caseItemMapper).insert(checks.capture());
        assertEquals("checkbox", checks.getValue().getItemType());
        var routes = ArgumentCaptor.forClass(RegistrationCaseRouteDO.class);
        verify(caseRouteMapper).insert(routes.capture());
        assertEquals("content_director", routes.getValue().getAssigneeType());
    }

    @Test void historicalTerminalAggregateIsNotRegenerated() {
        var order = new SalesOrderDO(); order.setId(10L);
        var historical = new RegistrationCaseDO(); historical.setId(99L); historical.setStatus("completed");
        when(orderMapper.selectByIdForUpdate(10L, 1L)).thenReturn(order);
        when(caseMapper.selectListByOrderId(10L)).thenReturn(List.of(historical));
        TenantContextHolder.setTenantId(1L);
        try { assertEquals(99L, service.ensureCaseAfterRegistrationApproval(10L, LocalDateTime.now())); }
        finally { TenantContextHolder.clear(); }
        verifyNoInteractions(orderItemMapper, templateMapper, caseItemMapper, registrationNotifyPublisher);
    }
}
