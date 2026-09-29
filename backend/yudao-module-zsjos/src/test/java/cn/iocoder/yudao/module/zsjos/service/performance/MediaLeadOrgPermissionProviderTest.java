package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadOrgMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaLeadOrgPermissionProviderTest {
    @InjectMocks private MediaLeadOrgPermissionProvider provider;
    @Mock private MediaLeadOrgMapper mapper;
    @Mock private MediaLeadAccess access;

    @Test void onlyConfiguredCenterInWritableScopeCanBeUnset() {
        var row = new MediaLeadOrgDO(); row.setKind("CENTER");
        when(access.has(MediaLeadAccess.TARGET_CONFIGURE)).thenReturn(true);
        when(access.writeDeptAllowed(10L)).thenReturn(true);
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);
        assertTrue(provider.hasPermission(10L, "unset", 1L));
        row.setKind("DEPT");
        assertFalse(provider.hasPermission(10L, "unset", 1L));
        verify(mapper, times(2)).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void missingFeatureOrObjectScopeNeverReadsCenter() {
        assertFalse(provider.hasPermission(10L, "unset", 1L));
        when(access.has(MediaLeadAccess.TARGET_CONFIGURE)).thenReturn(true);
        assertFalse(provider.hasPermission(10L, "unset", 1L));
        assertFalse(provider.hasPermission(10L, "update", 1L));
        verifyNoInteractions(mapper);
    }
}
