package cn.iocoder.yudao.module.zsjos.controller.admin.account;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountProfileService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/media-account/{id}/profile")
public class MediaAccountProfileController {
    @Resource private MediaAccountProfileService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:media-account:query')")
    public CommonResult<MediaAccountProfileVO> get(@PathVariable Long id){return success(service.get(id,getLoginUserId()));}
    @PutMapping
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<Integer> patch(@PathVariable Long id,@Valid @RequestBody Patch req){return success(service.patch(id,req,getLoginUserId()));}
    @PostMapping("/records")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<Integer> append(@PathVariable Long id,@Valid @RequestBody RecordRequest req){return success(service.append(id,req,getLoginUserId()));}
    @PostMapping("/diagnosis")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<Integer> diagnosis(@PathVariable Long id, @Valid @RequestBody DiagnosisRequest req) {
        return success(service.submitDiagnosis(id, req, getLoginUserId()));
    }
    @GetMapping("/history")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:query','zsjos:media-account:maintenance')")
    public CommonResult<PageResult<Entry>> history(@PathVariable Long id,@Valid HistoryQuery page){return success(service.history(id,page,getLoginUserId()));}
    @PostMapping("/files")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:media-account:edit','zsjos:media-account:maintenance')")
    public CommonResult<FileVO> upload(@PathVariable Long id,@RequestParam String fieldKey,@RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_ATTACHMENT_INVALID);
        return success(service.upload(id,fieldKey,file.getBytes(),file.getOriginalFilename(),file.getContentType(),getLoginUserId()));
    }
}
