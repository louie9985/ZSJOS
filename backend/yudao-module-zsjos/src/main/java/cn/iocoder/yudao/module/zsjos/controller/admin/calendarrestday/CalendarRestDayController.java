package cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo.*;
import cn.iocoder.yudao.module.zsjos.service.calendarrestday.CalendarRestDayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 首页休息日")
@RestController
@RequestMapping("/zsjos/calendar-rest-day")
@Validated
public class CalendarRestDayController {
    @Resource private CalendarRestDayService service;
    @GetMapping("/list")
    @Operation(summary = "读取首页休息日")
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<List<CalendarRestDayRespVO>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return success(service.list(TenantContextHolder.getRequiredTenantId(), getLoginUserId(), startDate, endDate));
    }
    @PutMapping("/batch-update")
    @Operation(summary = "批量管理首页休息日")
    @PreAuthorize("@ss.hasPermission('zsjos:calendar-rest-day:manage')")
    public CommonResult<List<CalendarRestDayRespVO>> update(@Valid @RequestBody CalendarRestDayBatchReqVO request) {
        return success(service.update(TenantContextHolder.getRequiredTenantId(), getLoginUserId(), request));
    }
}
