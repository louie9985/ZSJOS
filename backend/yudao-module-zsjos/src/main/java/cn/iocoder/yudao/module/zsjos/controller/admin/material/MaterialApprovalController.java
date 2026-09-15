package cn.iocoder.yudao.module.zsjos.controller.admin.material;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.*;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialApprovalService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @Validated @RequestMapping("/zsjos/material-approval")
public class MaterialApprovalController {
    @Resource private MaterialApprovalService service;
    @GetMapping("/types") @PreAuthorize("@ss.hasPermission('zsjos:material-approval:query')")
    public CommonResult<List<MaterialApprovalTypeRespVO>> types() { return success(service.types()); }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:material-approval:query')")
    public CommonResult<PageResult<MaterialApprovalRespVO>> page(@Valid MaterialApprovalPageReqVO request) {
        return success(service.page(request, getLoginUserId()));
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('zsjos:material-approval:query')")
    public CommonResult<MaterialApprovalRespVO> get(@RequestParam Long versionId, @RequestParam String taskId, @RequestParam(defaultValue="false") boolean done) {
        return success(service.get(versionId, taskId, done, getLoginUserId()));
    }
    @PostMapping("/approve") @PreAuthorize("@ss.hasPermission('zsjos:material-approval:approve') && @ss.hasPermission('zsjos:material-approval:query')")
    public CommonResult<Boolean> approve(@Valid @RequestBody MaterialApprovalDecisionReqVO request) {
        service.decide(request.getVersionId(), request.getTaskId(), request.getReason(), true, getLoginUserId()); return success(true);
    }
    @PostMapping("/reject") @PreAuthorize("@ss.hasPermission('zsjos:material-approval:reject') && @ss.hasPermission('zsjos:material-approval:query')")
    public CommonResult<Boolean> reject(@Valid @RequestBody MaterialApprovalDecisionReqVO request) {
        service.decide(request.getVersionId(), request.getTaskId(), request.getReason(), false, getLoginUserId()); return success(true);
    }
}
