package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountStudentCandidateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountCalendarPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountCalendarCandidatesRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountCalendarRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountCalendarScheduleReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountLegacyStageRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountMaintenanceReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountMaintenanceRevisionRespVO;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountService;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountMaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 第三方账号")
@RestController
@RequestMapping("/zsjos/media-account")
public class MediaAccountController {
    @Resource private MediaAccountService mediaAccountService;
    @Resource private MediaAccountMaintenanceService maintenanceService;

    @PostMapping("/create")
    @Operation(summary = "创建第三方平台账号")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:create')")
    public CommonResult<Long> create(@Valid @RequestBody MediaAccountSaveReqVO reqVO) {
        return success(mediaAccountService.create(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得第三方平台账号")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:query')")
    public CommonResult<MediaAccountRespVO> get(@RequestParam("id") Long id) {
        return success(mediaAccountService.get(id, getLoginUserId()));
    }
    @GetMapping("/page") @Operation(summary = "分页查询第三方平台账号")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:query')")
    public CommonResult<PageResult<MediaAccountRespVO>> page(@Valid MediaAccountPageReqVO reqVO) {
        return success(mediaAccountService.page(reqVO, getLoginUserId()));
    }

    @PutMapping("/{id}/maintenance")
    @Operation(summary = "维护账号当前状态")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:maintenance')")
    public CommonResult<Integer> maintain(@PathVariable Long id,
                                          @Valid @RequestBody MediaAccountMaintenanceReqVO reqVO) {
        return success(maintenanceService.maintain(id, reqVO, getLoginUserId()));
    }

    @GetMapping("/{id}/maintenance-history")
    @Operation(summary = "分页查询账号维护版本")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-account:maintenance')")
    public CommonResult<PageResult<MediaAccountMaintenanceRevisionRespVO>> maintenanceHistory(
            @PathVariable Long id, @Valid PageParam page) {
        return success(maintenanceService.history(id, page, getLoginUserId()));
    }

    @GetMapping("/{id}/legacy-stage-history")
    @Operation(summary = "分页查询账号原阶段记录")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-account:maintenance')")
    public CommonResult<PageResult<MediaAccountLegacyStageRespVO>> legacyStageHistory(
            @PathVariable Long id, @Valid PageParam page) {
        return success(maintenanceService.legacyStageHistory(id, page, getLoginUserId()));
    }

    @GetMapping("/calendar")
    @Operation(summary = "分页查询账号日历区间")
    @PreAuthorize("@ss.hasPermission('zsjos:media-calendar:query')")
    public CommonResult<MediaAccountCalendarRespVO> calendar(@Valid MediaAccountCalendarPageReqVO reqVO) {
        return success(maintenanceService.calendar(reqVO, getLoginUserId()));
    }

    @GetMapping("/calendar/all")
    @Operation(summary = "查询日历日程区间")
    @PreAuthorize("@ss.hasPermission('zsjos:media-calendar:all-query')")
    public CommonResult<MediaAccountCalendarRespVO> allCalendar(@Valid MediaAccountCalendarScheduleReqVO reqVO) {
        return success(maintenanceService.allCalendar(reqVO, getLoginUserId()));
    }

    @GetMapping("/calendar/candidates")
    @Operation(summary = "获得媒体账号日历筛选候选人")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-calendar:query','zsjos:media-calendar:all-query')")
    public CommonResult<MediaAccountCalendarCandidatesRespVO> calendarCandidates() {
        return success(maintenanceService.calendarCandidates(getLoginUserId()));
    }

    @GetMapping("/student-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:bind-student')")
    public CommonResult<List<MediaAccountStudentCandidateRespVO>> studentCandidates(
            @RequestParam(required = false) String keyword) {
        return success(mediaAccountService.studentCandidates(keyword, getLoginUserId()));
    }

    @PostMapping("/{id}/bind-student")
    @Operation(summary = "绑定学员")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:bind-student')")
    public CommonResult<Boolean> bindStudent(@PathVariable Long id, @RequestParam Long studentPersonId,
                                              @RequestParam(required = false) String reason) {
        mediaAccountService.bindStudent(id, studentPersonId, reason, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/unbind-student")
    @Operation(summary = "解绑学员")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:bind-student')")
    public CommonResult<Boolean> unbindStudent(@PathVariable Long id, @RequestParam(required = false) String reason) {
        mediaAccountService.unbindStudent(id, reason, getLoginUserId());
        return success(true);
    }

    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:media-account:edit')")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody MediaAccountUpdateReqVO req) { mediaAccountService.update(id, req, getLoginUserId()); return success(true); }

    @PostMapping("/{id}/rescue") @PreAuthorize("@ss.hasPermission('zsjos:media-account:rescue')")
    public CommonResult<Boolean> rescue(@PathVariable Long id, @RequestParam Integer version, @RequestParam String status) { mediaAccountService.updateRescue(id, version, status); return success(true); }
    @PostMapping("/{id}/request-rebind") @PreAuthorize("@ss.hasPermission('zsjos:media-account:rebind')")
    public CommonResult<String> requestRebind(@PathVariable Long id,@RequestParam Long targetStudentId,@RequestParam Integer version){return success(mediaAccountService.requestRebind(id,targetStudentId,version,getLoginUserId()));}
}
