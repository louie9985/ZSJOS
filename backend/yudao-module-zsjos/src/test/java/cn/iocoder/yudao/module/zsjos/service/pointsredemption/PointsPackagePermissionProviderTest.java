package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsPackageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsPackageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointsPackagePermissionProviderTest {
    @Test void requiresConfiguredSavePermissionAndVisiblePackage() {
        var provider = new PointsPackagePermissionProvider(); var mapper = mock(PointsPackageMapper.class);
        var permissions = mock(PermissionApi.class);
        ReflectionTestUtils.setField(provider,"packages",mapper); ReflectionTestUtils.setField(provider,"permissions",permissions);
        assertFalse(provider.hasPermission(9L,"save",1L)); verifyNoInteractions(mapper);
        when(permissions.hasAnyPermissions(1L,"zsjos:points-redemption:package-save")).thenReturn(true);
        assertFalse(provider.hasPermission(9L,"save",1L));
        when(mapper.selectById(9L)).thenReturn(new PointsPackageDO());
        assertTrue(provider.hasPermission(9L,"save",1L)); assertFalse(provider.hasPermission(9L,"unknown",1L));
    }
}
