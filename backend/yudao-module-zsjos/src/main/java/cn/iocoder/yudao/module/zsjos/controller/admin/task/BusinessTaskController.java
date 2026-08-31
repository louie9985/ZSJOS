package cn.iocoder.yudao.module.zsjos.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.MenuTaskSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import java.time.LocalDateTime;

@Tag(name = "管理后台 - 我的业务待办")
@RestController
@RequestMapping("/zsjos/business-task")
public class BusinessTaskController {
    @Resource private BusinessTaskService taskService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.task.MenuTaskSummaryService menuTaskSummaryService;

    @GetMapping("/menu-task-summary")
    @Operation(summary = "获得工作台菜单待办摘要")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:business-task:query','bpm:task:query')")
    public CommonResult<MenuTaskSummaryRespVO> getMenuTaskSummary() {
        return success(menuTaskSummaryService.getMySummary(getLoginUserId()));
    }

    @GetMapping("/my-summary")
    @Operation(summary = "获得我的待办汇总")
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<BusinessTaskSummaryRespVO> getMySummary() {
        return success(taskService.getMySummary(getLoginUserId()));
    }

    @GetMapping("/my-page")
    @Operation(summary = "获得我的待办分页")
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<PageResult<BusinessTaskRespVO>> getMyPage(
            @RequestParam("bucket") String bucket,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return success(taskService.getMyPage(getLoginUserId(), bucket, pageNo, pageSize));
    }

    @GetMapping("/my-task-page")
    @Operation(summary = "获得我的业务任务分页")
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<PageResult<BusinessTaskRespVO>> getMyTaskPage(@Valid BusinessTaskPageReqVO reqVO) {
        return success(taskService.getMyPage(getLoginUserId(), reqVO));
    }

    @PostMapping("/{id}/complete-birthday-care")
    @Operation(summary = "完成员工生日关怀待办")
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<Boolean> completeBirthdayCare(@PathVariable("id") Long id) {
        return success(commandService.completeEmployeeReminder(id, getLoginUserId(), LocalDateTime.now()));
    }

    @PostMapping("/{id}/complete-employee-reminder")
    @Operation(summary = "完成员工提醒待办")
    public CommonResult<Boolean> completeEmployeeReminder(@PathVariable("id") Long id) {
        return success(commandService.completeEmployeeReminder(id, getLoginUserId(), LocalDateTime.now()));
    }

    @Resource private cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService commandService;
}
