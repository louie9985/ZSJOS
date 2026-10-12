package cn.iocoder.yudao.module.hrm.controller.admin.employeereminder.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;

@Data
public class HrmLifecycleReminderConfigVO {
    @Valid @NotNull private Rule regular = new Rule();
    @Valid @NotNull private Rule leave = new Rule();
    @Valid @NotNull private Rule change = new Rule();
    @Data
    public static class Rule {
        private boolean enabled;
        @Min(0) @Max(30) private int advanceDays = 1;
        @NotBlank @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") private String triggerTime = "09:00";
        @NotNull private List<Long> deptIds = new ArrayList<>();
        private boolean includeChildDepartments;
        @NotNull private List<Long> recipientUserIds = new ArrayList<>();
        @NotNull private List<Long> recipientDeptIds = new ArrayList<>();
        private LocalDateTime enabledSince;
    }
}
