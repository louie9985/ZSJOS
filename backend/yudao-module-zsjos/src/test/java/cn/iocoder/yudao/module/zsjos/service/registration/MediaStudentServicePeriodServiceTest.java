package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.MediaStudentController;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServicePeriodUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaStudentServicePeriodServiceTest {
    @InjectMocks private MediaStudentServicePeriodService service;
    @Mock private PersonMapper personMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private StudentObjectPermissionProvider studentPermissions;

    @BeforeAll static void initMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), PersonDO.class);
    }

    @Test void deniedFeatureNeverReadsOrWritesPerson() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(10L, 20L, false));
        verifyNoInteractions(personMapper, studentPermissions);
    }

    @Test void fullReadDoesNotBypassResponsibility() {
        when(permissionApi.hasAnyPermissions(10L, MediaStudentServicePeriodService.PERMISSION)).thenReturn(true);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(10L, 20L, false));
        verifyNoInteractions(personMapper);
    }

    @Test void savesOnlyFlagAndReturnsPersistedValue() {
        when(permissionApi.hasAnyPermissions(10L, MediaStudentServicePeriodService.PERMISSION)).thenReturn(true);
        when(studentPermissions.hasPermission(20L, "update-service-period", 10L)).thenReturn(true);
        when(personMapper.selectCount(any())).thenReturn(1L);
        when(personMapper.selectById(20L)).thenReturn(new PersonDO().setId(20L).setInServicePeriod(false));
        doAnswer(invocation -> {
            PersonDO entity = invocation.getArgument(0);
            Wrapper<PersonDO> wrapper = invocation.getArgument(1);
            assertNull(entity.getName()); assertNull(entity.getInServicePeriod());
            assertTrue(wrapper.getSqlSet().contains("in_service_period"));
            assertFalse(wrapper.getSqlSet().contains("status"));
            assertTrue(wrapper.getSqlSegment().contains("id"));
            return 1;
        }).when(personMapper).update(any(PersonDO.class), any(Wrapper.class));
        assertFalse(service.update(10L, 20L, false));
        assertFalse(service.update(10L, 20L, false));
    }

    @Test void missingOrOtherTenantPersonCannotBeWritten() {
        when(permissionApi.hasAnyPermissions(10L, MediaStudentServicePeriodService.PERMISSION)).thenReturn(true);
        when(studentPermissions.hasPermission(20L, "update-service-period", 10L)).thenReturn(true);
        when(personMapper.selectCount(any())).thenReturn(0L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.update(10L, 20L, true));
        verify(personMapper, never()).update(any(PersonDO.class), any(Wrapper.class));
    }

    @Test void objectActionRequiresExactResponsibleRelation() {
        var provider = new StudentObjectPermissionProvider();
        var relations = mock(ServiceRelationMapper.class);
        ReflectionTestUtils.setField(provider, "relationMapper", relations);
        when(relations.isMediaStudentResponsible(20L, 10L)).thenReturn(true);
        assertTrue(provider.hasPermission(20L, "update-service-period", 10L));
        assertFalse(provider.hasPermission(20L, "update-service-period", 99L));
    }

    @Test void controllerAndServiceDeclareBothPermissionBoundaries() throws Exception {
        var controller = MediaStudentController.class.getMethod("updateServicePeriod", Long.class, StudentServicePeriodUpdateReqVO.class);
        assertTrue(controller.getAnnotation(PreAuthorize.class).value().contains(MediaStudentServicePeriodService.PERMISSION));
        var method = MediaStudentServicePeriodService.class.getMethod("update", Long.class, Long.class, boolean.class);
        assertEquals("update-service-period", method.getAnnotation(ZsjosPermission.class).action());
        assertNotNull(StudentServicePeriodUpdateReqVO.class.getDeclaredField("inServicePeriod")
                .getAnnotation(jakarta.validation.constraints.NotNull.class));
    }
}
