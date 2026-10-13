package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo.*;
import cn.iocoder.yudao.module.zsjos.service.pointsredemption.*;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @Validated @RequestMapping("/zsjos/points-redemption")
public class PointsRedemptionController {
    @Resource private PointsRequestService service;
    @Resource private PointsIssuanceService issuance;
    @Resource private PointsRequestRateLimit rateLimit;
    @Resource private PointsApprovalProgressService approvalProgress;
    @GetMapping("/approval") @PreAuthorize("@ss.hasAnyPermissions('zsjos:points-redemption:query','bpm:task:query')")
    public CommonResult<PointsApprovalRespVO> approval(@RequestParam Long id) { return success(approvalProgress.get(id, getLoginUserId())); }
    @PostMapping("/urge") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:urge')")
    public CommonResult<Boolean> urge(@RequestParam Long id, @Valid @RequestBody PointsUrgeReqVO req) {
        approvalProgress.urge(id, req.getIdempotencyKey(), getLoginUserId()); return success(true);
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:query')")
    public CommonResult<PageResult<PointsRequestRespVO>> page(@Valid PageParam page,
            @RequestParam(defaultValue="false") boolean management) { return success(service.page(page, getLoginUserId(), management)); }
    @GetMapping("/get") @PreAuthorize("@ss.hasAnyPermissions('zsjos:points-redemption:query','bpm:task:query')")
    public CommonResult<PointsRequestRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:create')")
    @ApiAccessLog(requestEnable=false)
    public CommonResult<Long> create(@Valid @RequestBody PointsCreateReqVO req) { rateLimit.check("create", getLoginUserId(), 10); return success(service.create(req, getLoginUserId())); }
    @PostMapping("/upload") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:create')")
    @ApiAccessLog(requestEnable=false)
    public CommonResult<FileInfoRespDTO> upload(@RequestParam MultipartFile file) throws java.io.IOException {
        if (file.getSize() > 20 * 1024 * 1024) throw PointsErrors.invalid("附件不能超过20MB");
        return success(service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType(), getLoginUserId()));
    }
    @PostMapping("/reveal") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:reveal')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<String> reveal(@RequestParam Long id, HttpServletResponse response) {
        rateLimit.check("reveal", getLoginUserId(), 30);
        response.setHeader("Cache-Control", "no-store"); return success(service.reveal(id));
    }
    @PostMapping("/cancel") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:cancel')")
    public CommonResult<Boolean> cancel(@RequestParam Long id) { service.cancel(id, getLoginUserId()); return success(true); }
    @PostMapping("/retry") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:retry')")
    public CommonResult<Boolean> retry(@RequestParam Long id) { service.retry(id); return success(true); }
    @PostMapping("/refresh") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:refresh')")
    public CommonResult<Boolean> refresh(@RequestParam Long id) { rateLimit.check("refresh", getLoginUserId(), 10); issuance.refresh(id); return success(true); }
    @PostMapping("/disable") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:disable')")
    public CommonResult<Boolean> disable(@RequestParam Long id) { issuance.disable(id); return success(true); }
    @GetMapping("/packages") @PreAuthorize("@ss.hasAnyPermissions('zsjos:points-redemption:create','zsjos:points-redemption:package')")
    public CommonResult<List<PointsPackageRespVO>> packages() { return success(service.packages(false)); }
    @GetMapping("/package-list") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:package')")
    public CommonResult<List<PointsPackageRespVO>> packageList() { return success(service.packages(true)); }
    @PostMapping("/package-save") @PreAuthorize("@ss.hasPermission('zsjos:points-redemption:package-save')")
    public CommonResult<Long> savePackage(@Valid @RequestBody PointsPackageSaveReqVO req) {
        return success(req.getId() == null ? service.createPackage(req) : service.updatePackage(req));
    }
}
