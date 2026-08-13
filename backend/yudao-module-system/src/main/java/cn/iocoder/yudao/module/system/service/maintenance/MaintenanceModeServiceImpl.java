package cn.iocoder.yudao.module.system.service.maintenance;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.module.system.enums.LogRecordConstants.*;

@Service
public class MaintenanceModeServiceImpl implements MaintenanceModeService, MaintenanceModeApi {
    public static final String CONFIG_KEY = "zsjos.system.maintenance-enabled";
    @Resource private ConfigApi configApi;

    @Override
    public boolean isEnabled() {
        return Boolean.parseBoolean(configApi.getConfigValueByKey(CONFIG_KEY));
    }

    @Override
    @LogRecord(type = SYSTEM_MAINTENANCE_TYPE, subType = SYSTEM_MAINTENANCE_UPDATE_SUB_TYPE,
            bizNo = "global", success = SYSTEM_MAINTENANCE_UPDATE_SUCCESS)
    public void update(boolean enabled) {
        configApi.updateConfigValueByKey(CONFIG_KEY, Boolean.toString(enabled));
    }
}
