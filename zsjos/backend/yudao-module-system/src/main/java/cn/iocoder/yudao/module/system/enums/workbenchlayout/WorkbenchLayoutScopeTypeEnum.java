package cn.iocoder.yudao.module.system.enums.workbenchlayout;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WorkbenchLayoutScopeTypeEnum {

    GLOBAL("GLOBAL"),
    ROLE("ROLE");

    private final String type;

    public static WorkbenchLayoutScopeTypeEnum of(String type) {
        for (WorkbenchLayoutScopeTypeEnum value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return null;
    }

}
