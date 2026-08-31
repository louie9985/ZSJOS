package cn.iocoder.yudao.module.hrm.api.employee.event;

/**
 * Employee lifecycle facts exposed to dependent business modules.
 */
public enum HrmEmployeeLifecycleEventType {

    ACCOUNT_BOUND,
    ENTRY_CONFIRMED,
    REHIRED,
    CHANGE_EFFECTIVE,
    QUIT_PLANNED,
    QUIT_CANCELLED,
    LEFT

}
