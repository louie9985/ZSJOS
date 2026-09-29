package cn.iocoder.yudao.module.zsjos.controller.admin.calendar;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyUserPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifySendRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyPreviewRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyRecipientRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 日历通知")
@RestController
@Validated
@RequestMapping("/zsjos/calendar-notification")
public class CalendarNotificationController {
    @Resource private CalendarNotificationService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarMaintenanceNotificationService maintenance;

    @GetMapping("/users")
    @Operation(summary = "查询可通知员工")
    @PreAuthorize("@calendarNotificationAccess.check(#req.calendarType, false)")
    public CommonResult<PageResult<CalendarNotifyUserRespVO>> users(@Valid CalendarNotifyUserPageReqVO req) {
        return success(service.getUsers(req));
    }

    @PostMapping("/send")
    @Operation(summary = "发送日历通知")
    @PreAuthorize("@calendarNotificationAccess.check(#req.calendarType, 'ALL'.equalsIgnoreCase(#req.scope))")
    public CommonResult<CalendarNotifySendRespVO> send(@Valid @RequestBody CalendarNotifyReqVO req) {
        return success(service.send(req));
    }

    @PostMapping("/preview")
    @Operation(summary = "预览日历通知")
    @PreAuthorize("@calendarNotificationAccess.check(#req.calendarType, 'ALL'.equalsIgnoreCase(#req.scope))")
    public CommonResult<CalendarNotifyPreviewRespVO> preview(@Valid @RequestBody CalendarNotifyReqVO req) {
        return success(req.getMaintenanceAction() == null ? service.preview(req) : maintenance.preview(req));
    }

    @GetMapping("/operation")
    @Operation(summary = "查询本人日历维护的通知结果")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:exam-calendar:manage', 'zsjos:course-calendar:manage')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarMaintenanceResultRespVO> operation(
            @RequestParam @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128) String operationKey) {
        return success(maintenance.getResult(operationKey));
    }

    @GetMapping("/batch/{id}")
    @Operation(summary = "查询日历通知批次")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:exam-calendar:notify', 'zsjos:course-calendar:notify')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchRespVO> batch(@PathVariable Long id) {
        return success(service.getBatch(id));
    }

    @GetMapping("/batch-page")
    @Operation(summary = "分页查询日历通知批次")
    @PreAuthorize("@calendarNotificationAccess.check(#req.calendarType, false)")
    public CommonResult<PageResult<CalendarNotifyBatchRespVO>> batchPage(@Valid CalendarNotifyBatchPageReqVO req) {
        return success(service.getBatchPage(req));
    }

    @GetMapping("/batch/{id}/recipients")
    @Operation(summary = "分页查询日历通知接收人")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:exam-calendar:notify', 'zsjos:course-calendar:notify')")
    public CommonResult<PageResult<CalendarNotifyRecipientRespVO>> recipients(@PathVariable Long id, @Valid PageParam req) {
        return success(service.getRecipientPage(id, req));
    }
}
