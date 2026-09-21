package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialTenantReadTest {
    @Mock MaterialMapper materialMapper;
    @Mock MaterialVersionMapper versionMapper;
    @Mock MaterialFileMapper materialFileMapper;
    @Mock PermissionApi permissionApi;
    @InjectMocks MaterialService service;
    @InjectMocks MaterialObjectPermissionProvider provider;

    @Test void administratorSeesForeignDraftButCannotEditOrSubmitIt() {
        when(materialMapper.selectById(1L)).thenReturn(new MaterialDO().setId(1L).setOwnerUserId(20L).setStatus("DRAFT"));
        when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        when(versionMapper.selectById(2L)).thenReturn(new MaterialVersionDO().setId(2L).setMaterialId(1L)
                .setStatus("DRAFT").setValuesJson("{}").setFieldSnapshotJson("[]").setDictSnapshotJson("{}"));
        assertTrue(provider.hasPermission(1L, "read", 9L));
        assertFalse(provider.hasPermission(1L, "edit", 9L));
        assertFalse(provider.hasPermission(1L, "submit", 9L));
        assertEquals("DRAFT", service.getVersion(2L, 9L).getStatus());
        verify(materialMapper, never()).updateById(any(MaterialDO.class));
    }
}
