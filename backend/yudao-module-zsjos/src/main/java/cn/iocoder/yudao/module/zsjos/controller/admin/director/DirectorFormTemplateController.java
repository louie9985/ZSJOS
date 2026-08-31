package cn.iocoder.yudao.module.zsjos.controller.admin.director;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService.*;

@Tag(name = "管理后台 - 编导表单模板")
@RestController
@RequestMapping("/zsjos")
@Validated
public class DirectorFormTemplateController {
    @Resource private DirectorFormTemplateService service;

    @GetMapping("/director-interview-template/list")
    @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:query')")
    public CommonResult<List<DirectorFormTemplateVO.TemplateResp>> interviewList() { return success(service.list(SCENE_INTERVIEW)); }
    @GetMapping("/director-interview-template/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:query')")
    public CommonResult<DirectorFormTemplateVO.TemplateResp> interviewGet(@PathVariable Long id) { return success(service.get(id, SCENE_INTERVIEW)); }
    @PostMapping("/director-interview-template/{id}/draft/copy")
    @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:update')")
    public CommonResult<Long> interviewCopy(@PathVariable Long id, @RequestParam Integer version) { return success(service.copyDraft(id, version, SCENE_INTERVIEW)); }
    @PutMapping("/director-interview-template/{id}/draft")
    @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:update')")
    public CommonResult<Boolean> interviewUpdate(@PathVariable Long id, @Valid @RequestBody DirectorFormTemplateVO.SaveDraftReq req) { service.updateDraft(id, req, SCENE_INTERVIEW); return success(true); }
    @PostMapping("/director-interview-template/{id}/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:publish')")
    public CommonResult<Boolean> interviewPublish(@PathVariable Long id, @Valid @RequestBody DirectorFormTemplateVO.PublishReq req) { service.publish(id, req, getLoginUserId(), SCENE_INTERVIEW); return success(true); }

    @GetMapping("/positioning-template/list")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:query')")
    public CommonResult<List<DirectorFormTemplateVO.TemplateResp>> positioningList() { return success(service.list(SCENE_POSITIONING)); }
    @GetMapping("/positioning-template/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:query')")
    public CommonResult<DirectorFormTemplateVO.TemplateResp> positioningGet(@PathVariable Long id) { return success(service.get(id, SCENE_POSITIONING)); }
    @PostMapping("/positioning-template")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:create')")
    public CommonResult<Long> positioningCreate(@Valid @RequestBody DirectorFormTemplateVO.CreateReq req) { return success(service.createPositioning(req)); }
    @PostMapping("/positioning-template/{id}/draft/copy")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:update')")
    public CommonResult<Long> positioningCopy(@PathVariable Long id, @RequestParam Integer version) { return success(service.copyDraft(id, version, SCENE_POSITIONING)); }
    @PutMapping("/positioning-template/{id}/draft")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:update')")
    public CommonResult<Boolean> positioningUpdate(@PathVariable Long id, @Valid @RequestBody DirectorFormTemplateVO.SaveDraftReq req) { service.updateDraft(id, req, SCENE_POSITIONING); return success(true); }
    @PostMapping("/positioning-template/{id}/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:publish')")
    public CommonResult<Boolean> positioningPublish(@PathVariable Long id, @Valid @RequestBody DirectorFormTemplateVO.PublishReq req) { service.publish(id, req, getLoginUserId(), SCENE_POSITIONING); return success(true); }
    @DeleteMapping("/positioning-template/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-template:delete')")
    public CommonResult<Boolean> positioningDelete(@PathVariable Long id) { service.deleteUnusedPositioning(id); return success(true); }
}
