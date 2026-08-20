package cn.iocoder.yudao.module.zsjos.job.birthdaycare;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareApi;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareDispatch;
import cn.iocoder.yudao.module.hrm.api.birthdaycare.HrmBirthdayCareEmployee;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.service.birthdaycare.BirthdayCareConstants;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class EmployeeBirthdayCareJob implements JobHandler {
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM月dd日");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    @Resource private HrmBirthdayCareApi birthdayCareApi;
    @Resource private BusinessTaskCommandService taskCommandService;
    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;

    @Override
    @TenantJob
    public String execute(String param) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        HrmBirthdayCareDispatch dispatch = birthdayCareApi.getDueDispatch(now.toLocalDate(), now.toLocalTime());
        if (dispatch.isEmpty()) return "生日关怀：无待处理员工或接收人";
        int created = 0;
        for (HrmBirthdayCareEmployee employee : dispatch.employees()) {
            for (Long recipientUserId : dispatch.recipientUserIds()) {
                String key = "hrm-birthday-care:" + dispatch.targetBirthdayDate().getYear() + ":"
                        + employee.id() + ":" + recipientUserId;
                try {
                    String summary = (employee.deptName() == null ? "" : employee.deptName() + " · ")
                            + employee.birthday().format(MONTH_DAY);
                    taskCommandService.create(new BusinessTaskCreateCommand(
                            BirthdayCareConstants.TASK_TYPE, BirthdayCareConstants.BIZ_TYPE, employee.id(), recipientUserId,
                            "生日关怀：" + employee.name(), summary, BirthdayCareConstants.ACTION_COMPLETE,
                            dispatch.dueAt(), dispatch.dueAt(), null, key));
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("recipientUserId", recipientUserId);
                    payload.put("employeeName", employee.name());
                    payload.put("departmentName", employee.deptName());
                    payload.put("birthday", employee.birthday().format(MONTH_DAY));
                    notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                            .tenantId(TenantContextHolder.getRequiredTenantId())
                            .sceneCode(BirthdayCareConstants.SCENE).sourceEventKey(key)
                            .bizType(BirthdayCareConstants.BIZ_TYPE).bizId(employee.id())
                            .occurredAt(now).payload(payload).build());
                    created++;
                } catch (RuntimeException exception) {
                    log.warn("生日关怀单条处理失败，租户={}, 员工={}, 接收人={}",
                            TenantContextHolder.getRequiredTenantId(), employee.id(), recipientUserId);
                }
            }
        }
        return "生日关怀：处理 " + created + " 条";
    }
}
