package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionReminderService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/exam-collection-planner")
public class ExamCollectionReminderController {
    @Resource private ExamCollectionReminderService service;
    public record Acknowledge(@NotNull @Size(min=1,max=100) List<@NotNull Long> ids) {}
    @GetMapping("/pending")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<List<ExamCollectionReminderService.Todo>> pending(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(service.pending());
    }
    @GetMapping("/reminders")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<List<ExamCollectionReminderService.Todo>> reminders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(service.reminders());
    }
    @PostMapping("/acknowledge")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:business-task:query')")
    public CommonResult<Boolean> acknowledge(@Valid @RequestBody Acknowledge request) {
        service.acknowledge(request.ids()); return success(true);
    }
}
