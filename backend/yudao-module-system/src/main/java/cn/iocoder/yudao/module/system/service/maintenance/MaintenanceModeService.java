package cn.iocoder.yudao.module.system.service.maintenance;

public interface MaintenanceModeService {
    boolean isEnabled();
    void update(boolean enabled);
}
