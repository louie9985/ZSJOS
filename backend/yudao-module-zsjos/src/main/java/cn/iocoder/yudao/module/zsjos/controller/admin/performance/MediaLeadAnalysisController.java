package cn.iocoder.yudao.module.zsjos.controller.admin.performance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO;
import cn.iocoder.yudao.module.zsjos.service.performance.MediaLeadAnalysisService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/zsjos/media-lead-analysis")
@PreAuthorize("@ss.hasPermission('zsjos:media-lead-analysis:query')")
public class MediaLeadAnalysisController {
    @Resource private MediaLeadAnalysisService service;

    @GetMapping("/overview")
    public CommonResult<MediaLeadVO.Overview> overview(@Valid MediaLeadVO.Query query) {
        return success(service.overview(query));
    }

    @GetMapping("/details")
    @PreAuthorize("@ss.hasPermission('zsjos:media-lead-analysis:detail')")
    public CommonResult<List<MediaLeadVO.Detail>> details(@Valid MediaLeadVO.Query query) {
        return success(service.details(query));
    }

    @GetMapping("/detail-page")
    @PreAuthorize("@ss.hasPermission('zsjos:media-lead-analysis:detail')")
    public CommonResult<cn.iocoder.yudao.framework.common.pojo.PageResult<MediaLeadVO.Detail>> detailPage(
            @Valid MediaLeadVO.DetailPageQuery query) {
        return success(service.detailPage(query));
    }

    @GetMapping("/tree")
    public CommonResult<List<MediaLeadVO.ScopeNode>> tree() {
        return success(service.tree());
    }
}

@RestController
@Validated
@RequestMapping("/zsjos/media-lead-target")
@PreAuthorize("@ss.hasPermission('zsjos:media-lead-target:query')")
class MediaLeadTargetController {
    @Resource private MediaLeadAnalysisService service;

    @GetMapping("/list")
    public CommonResult<List<MediaLeadVO.Target>> list(@RequestParam @NotNull LocalDate periodStart) {
        return success(service.listTargets(periodStart));
    }

    @PutMapping("/batch")
    @PreAuthorize("@ss.hasPermission('zsjos:media-lead-target:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody List<MediaLeadVO.TargetEdit> edits) {
        service.saveTargets(edits);
        return success(true);
    }

    @GetMapping("/{id}/revisions")
    public CommonResult<List<MediaLeadVO.TargetRevision>> revisions(@PathVariable Long id) {
        return success(service.revisions(id));
    }

    @GetMapping("/orgs")
    public CommonResult<List<MediaLeadVO.Org>> orgs() { return success(service.organizations()); }

    @GetMapping("/departments")
    public CommonResult<List<MediaLeadVO.DeptOption>> departments() { return success(service.departmentOptions()); }

    @PutMapping("/org")
    @PreAuthorize("@ss.hasPermission('zsjos:media-lead-target:configure')")
    public CommonResult<Boolean> saveOrg(@Valid @RequestBody MediaLeadVO.OrgEdit edit) {
        service.saveOrganization(edit);
        return success(true);
    }

    @PutMapping("/org/unset")
    @PreAuthorize("@ss.hasPermission('zsjos:media-lead-target:configure')")
    public CommonResult<Boolean> unsetOrg(@Valid @RequestBody MediaLeadVO.OrgUnset edit) {
        service.unsetOrganization(edit);
        return success(true);
    }
}
