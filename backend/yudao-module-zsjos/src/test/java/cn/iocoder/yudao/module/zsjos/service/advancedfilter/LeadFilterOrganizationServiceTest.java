package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO.OptionVO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadFilterOrganizationServiceTest {
    @InjectMocks LeadFilterOrganizationService service;
    @Mock DeptApi deptApi;
    @Mock AdminUserApi adminUserApi;
    DeptRespDTO dept(long id, long parent, String name) {
        var dept = new DeptRespDTO(); dept.setId(id); dept.setParentId(parent); dept.setName(name); return dept;
    }
    @Test void expandsSelectedOrganizationThroughSystemAndDeduplicatesOwners() {
        when(deptApi.getDeptList(Set.of(10L))).thenReturn(List.of(dept(10,0,"中心")));
        when(deptApi.getChildDeptList(Set.of(10L))).thenReturn(List.of(dept(11,10,"部门")));
        var user = new AdminUserRespDTO(); user.setId(20L);
        when(adminUserApi.getUserListByDeptIds(Set.of(10L,11L))).thenReturn(List.of(user,user));
        assertEquals(List.of(20L), service.ownerIds(List.of("10")));
    }
    @Test void removedSelectionFailsAndNoUsersReturnsEmptyWithoutBroadening() {
        when(deptApi.getDeptList(Set.of(10L))).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.ownerIds(List.of("10")));
        when(deptApi.getDeptList(Set.of(10L))).thenReturn(List.of(dept(10,0,"中心")));
        when(deptApi.getChildDeptList(Set.of(10L))).thenReturn(List.of());
        when(adminUserApi.getUserListByDeptIds(Set.of(10L))).thenReturn(List.of());
        assertEquals(List.of(),service.ownerIds(List.of("10")));
    }
    @Test void optionsUseOnlyVisibleUsersAndTheirActualSystemAncestors() {
        var user = new AdminUserRespDTO(); user.setId(20L); user.setDeptId(11L);
        when(adminUserApi.getUserList(List.of(20L))).thenReturn(List.of(user));
        when(deptApi.getDeptList(Set.of(11L))).thenReturn(List.of(dept(11,10,"部门")));
        when(deptApi.getParentDeptList(11L)).thenReturn(List.of(dept(10,0,"中心")));
        assertEquals(List.of(new OptionVO("10","中心"),new OptionVO("11","中心 / 部门")), service.options(List.of(new OptionVO("20","人员"))));
        assertEquals(List.of(),service.options(List.of()));
    }
}
