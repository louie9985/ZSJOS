package cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService.SCENE_POSITIONING_INTERVIEW;
@RestController @Validated @RequestMapping("/zsjos/positioning-interview-template")
public class PositioningInterviewTemplateController {
 @Resource private DirectorFormTemplateService service;
 @GetMapping("/list") @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:query')")
 public CommonResult<List<DirectorFormTemplateVO.TemplateResp>> list(){return success(service.list(SCENE_POSITIONING_INTERVIEW));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:query')")
 public CommonResult<DirectorFormTemplateVO.TemplateResp> get(@PathVariable Long id){return success(service.get(id,SCENE_POSITIONING_INTERVIEW));}
 @PostMapping("/{id}/draft/copy") @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:update')")
 public CommonResult<Long> copy(@PathVariable Long id,@RequestParam Integer version){return success(service.copyDraft(id,version,SCENE_POSITIONING_INTERVIEW));}
 @PutMapping("/{id}/draft") @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:update')")
 public CommonResult<Boolean> update(@PathVariable Long id,@Valid @RequestBody DirectorFormTemplateVO.SaveDraftReq req){service.updateDraft(id,req,SCENE_POSITIONING_INTERVIEW);return success(true);}
 @PostMapping("/{id}/publish") @PreAuthorize("@ss.hasPermission('zsjos:director-interview-template:publish')")
 public CommonResult<Boolean> publish(@PathVariable Long id,@Valid @RequestBody DirectorFormTemplateVO.PublishReq req){service.publish(id,req,getLoginUserId(),SCENE_POSITIONING_INTERVIEW);return success(true);}
}

