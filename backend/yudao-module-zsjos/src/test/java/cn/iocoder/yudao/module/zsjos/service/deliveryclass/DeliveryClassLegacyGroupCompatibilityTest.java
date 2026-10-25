package cn.iocoder.yudao.module.zsjos.service.deliveryclass;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryClassLegacyGroupCompatibilityTest {
    @Test void oldAdminMetadataEditKeepsMembershipAndFrozenDateWithoutTouchingServices() {
        TenantContextHolder.setTenantId(1L);
        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),DeliveryClassDO.class);
            var mapper=mock(DeliveryClassMapper.class);var users=mock(AdminUserApi.class);
            var permissions=mock(PermissionApi.class);var roles=mock(RoleApi.class);var tasks=mock(BusinessTaskCommandService.class);
            var current=new DeliveryClassDO().setId(5L).setGroupId(77L).setClassName("旧班名").setClassKind("NORMAL")
                    .setSystemClass(false).setDeptId(80L).setHomeroomUserId(20L).setStatus("SERVING").setVersion(0)
                    .setExamScheduleSnapshot("2026-12-01");
            when(mapper.selectByIdForUpdate(5L,1L)).thenReturn(current);
            when(mapper.update(eq(current),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
            var manager=new AdminUserRespDTO().setId(9L).setDeptId(80L).setStatus(0);
            var teacher=new AdminUserRespDTO().setId(20L).setDeptId(80L).setStatus(0);
            when(users.getUser(9L)).thenReturn(manager);when(users.getUser(20L)).thenReturn(teacher);
            var role=new RoleRespDTO();role.setId(3L);role.setStatus(0);when(roles.getRoleByCode("study_planner")).thenReturn(role);
            when(permissions.getEnabledRoleIdsByUserId(20L)).thenReturn(Set.of(3L));
            var service=new DeliveryClassServiceImpl();
            Object[][] fields={{"mapper",mapper},{"adminUserApi",users},{"permissionApi",permissions},{"roleApi",roles},{"taskCommandService",tasks}};
            for(var f:fields)ReflectionTestUtils.setField(service,(String)f[0],f[1]);
            var request=new DeliveryClassSaveReqVO();request.setClassName("新班名");request.setHomeroomUserId(20L);
            request.setStartDate(java.time.LocalDate.of(2026,12,1));request.setPreserveExamDate(true);request.setVersion(0);
            service.update(5L,request,9L);
            assertEquals(77L,current.getGroupId());assertEquals("新班名",current.getClassName());
            assertEquals("2026-12-01",current.getExamScheduleSnapshot());assertEquals(1,current.getVersion());
            verifyNoInteractions(tasks);
        } finally { TenantContextHolder.clear(); }
    }
}
