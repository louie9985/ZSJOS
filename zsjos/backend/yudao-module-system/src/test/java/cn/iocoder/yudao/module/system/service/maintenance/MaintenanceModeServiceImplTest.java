package cn.iocoder.yudao.module.system.service.maintenance;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeServiceImplTest {
    @InjectMocks private MaintenanceModeServiceImpl service;
    @Mock private ConfigApi configApi;

    @Test
    void absentConfigurationDefaultsToDisabled() {
        assertFalse(service.isEnabled());
    }

    @Test
    void updateUsesFixedSystemConfiguration() {
        service.update(true);
        verify(configApi).updateConfigValueByKey(MaintenanceModeServiceImpl.CONFIG_KEY, "true");
    }
}
