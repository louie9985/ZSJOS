package cn.iocoder.yudao.module.zsjos.controller.admin.account;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountDiagnosisReminderService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/zsjos/media-account/diagnosis")
public class MediaAccountDiagnosisController {
    @Resource private MediaAccountDiagnosisReminderService service;
    @GetMapping("/reminders")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<List<DiagnosisTodo>> reminders() {return success(service.reminders(getLoginUserId()));}
    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<List<DiagnosisTodo>> tasks(@RequestParam Long accountId) {return success(service.accountTasks(accountId,getLoginUserId()));}
    @PostMapping("/acknowledge")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<Boolean> acknowledge(@Valid @RequestBody ReminderAck request) {service.acknowledge(getLoginUserId(),request.getTaskIds());return success(true);}
}
