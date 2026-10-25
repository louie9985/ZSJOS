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
    @Resource private cn.iocoder.yudao.module.zsjos.service.account.WeeklyDiagnosisService weekly;
    @Resource private cn.iocoder.yudao.module.zsjos.service.account.AccountDiagnosisBatchService batches;
    @GetMapping("/batch-candidates")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<List<cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisBatchVO.Candidate>> batchCandidates(
            @RequestParam Long accountId, @RequestParam String templateType, @RequestParam(required=false) String nodeKey) {
        return success(batches.candidates(accountId, templateType, nodeKey, getLoginUserId()));
    }
    @PostMapping("/batch")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<List<cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisBatchVO.Result>> submitBatch(
            @Valid @RequestBody cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisBatchVO.Submit request) {
        return success(batches.submit(request, getLoginUserId()));
    }
    @GetMapping("/overview")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Overview> overview(@RequestParam Long accountId) {return success(weekly.overview(accountId,getLoginUserId()));}
    @GetMapping("/nodes")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<List<cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Node>> nodes(@RequestParam Long accountId) {return success(weekly.nodes(accountId,getLoginUserId()));}
    @GetMapping("/preview")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Preview> preview(@RequestParam Long accountId,@RequestParam String nodeKey,@RequestParam(required=false) Long highestLikes) {return success(weekly.preview(accountId,nodeKey,highestLikes,getLoginUserId()));}
    @GetMapping("/entry-metrics")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-account:maintenance')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Preview> entryMetrics(@RequestParam Long accountId,@RequestParam Long entryId,@RequestParam(required=false) Long highestLikes) {return success(weekly.history(accountId,entryId,highestLikes,getLoginUserId()));}
    @PostMapping("/weekly")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<Integer> submit(@RequestParam Long accountId,@Valid @RequestBody cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Submit request) {return success(weekly.submit(accountId,request,getLoginUserId()));}
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
