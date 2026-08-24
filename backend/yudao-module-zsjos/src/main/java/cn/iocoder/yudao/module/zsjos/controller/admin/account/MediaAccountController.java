package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountStudentCandidateRespVO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountService;
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

    @GetMapping("/student-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:bind-student')")
    public CommonResult<List<MediaAccountStudentCandidateRespVO>> studentCandidates(
            @RequestParam(required = false) String keyword) {
        return success(mediaAccountService.studentCandidates(keyword, getLoginUserId()));
    }

    @PostMapping("/{id}/advance-stage")
    @Operation(summary = "推进账号阶段")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:stage-advance')")
    public CommonResult<Boolean> stageAdvance(@PathVariable Long id, @RequestParam String toStage,
                                               @RequestParam Integer version,
                                               @RequestParam(required = false) String criteriaSnapshotJson,
                                               @RequestParam(required = false) String basis,
                                               @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        mediaAccountService.advanceStage(id, toStage, version, criteriaSnapshotJson, basis,
                idempotencyKey == null ? "stage:" + id + ":" + version + ":" + toStage : idempotencyKey,
                getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/rollback-stage")
    @Operation(summary = "回退账号阶段")
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:stage-rollback')")
    public CommonResult<Boolean> stageRollback(@PathVariable Long id, @RequestParam String toStage,
                                               @RequestParam Integer version,
                                               @RequestParam(required = false) String criteriaSnapshotJson,
                                               @RequestParam(required = false) String basis,
                                               @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        mediaAccountService.rollbackStage(id, toStage, version, criteriaSnapshotJson, basis,
                idempotencyKey == null ? "stage-rollback:" + id + ":" + version + ":" + toStage : idempotencyKey,
                getLoginUserId());
        return success(true);
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

    @PostMapping("/{id}/diagnoses") @PreAuthorize("@ss.hasPermission('zsjos:media-account:diagnose')")
    public CommonResult<Long> diagnose(@PathVariable Long id, @Valid @RequestBody AccountDiagnosisSaveReqVO req) { return success(mediaAccountService.diagnose(id, req, getLoginUserId())); }

    @GetMapping("/{id}/diagnoses") @PreAuthorize("@ss.hasPermission('zsjos:media-account:query')")
    public CommonResult<List<AccountDiagnosisRespVO>> diagnoses(@PathVariable Long id) { return success(mediaAccountService.diagnoses(id, getLoginUserId()).stream().map(x -> BeanUtils.toBean(x, AccountDiagnosisRespVO.class)).toList()); }
    @GetMapping("/diagnosis-config/published") @PreAuthorize("@ss.hasPermission('zsjos:media-account:diagnose')")
    public CommonResult<Long> publishedDiagnosisConfig() { return success(mediaAccountService.getPublishedDiagnosisConfigId()); }

    @PostMapping("/{id}/rescue") @PreAuthorize("@ss.hasPermission('zsjos:media-account:rescue')")
    public CommonResult<Boolean> rescue(@PathVariable Long id, @RequestParam Integer version, @RequestParam String status) { mediaAccountService.updateRescue(id, version, status); return success(true); }
    @PostMapping("/{id}/request-rebind") @PreAuthorize("@ss.hasPermission('zsjos:media-account:rebind')")
    public CommonResult<String> requestRebind(@PathVariable Long id,@RequestParam Long targetStudentId,@RequestParam Integer version){return success(mediaAccountService.requestRebind(id,targetStudentId,version,getLoginUserId()));}
}
