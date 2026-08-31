package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.NotBlankIdempotency;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student-contact-config")
@Validated
public class StudentContactConfigController {
    @Resource private StudentContactConfigService service;
    @GetMapping @PreAuthorize("@ss.hasPermission('zsjos:student-contact-config:query')")
    public CommonResult<StudentContactConfigRespVO> get() { return success(service.get()); }
    @PostMapping("/draft/copy") @PreAuthorize("@ss.hasPermission('zsjos:student-contact-config:update')")
    public CommonResult<Long> copy(@Valid @RequestBody CopyReq request) {
        return success(service.copyDraft(request.getPublishedId(), request.getPublishedVersion(), request.getIdempotencyKey()));
    }
    @PutMapping("/draft") @PreAuthorize("@ss.hasPermission('zsjos:student-contact-config:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody StudentContactConfigSaveReqVO request) {
        service.updateDraft(request); return success(true);
    }
    @PostMapping("/publish") @PreAuthorize("@ss.hasPermission('zsjos:student-contact-config:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody PublishReq request) {
        service.publish(request.getId(), request.getVersion(), request.getIdempotencyKey()); return success(true);
    }
    @Data public static class CopyReq {
        @NotNull private Long publishedId;
        @NotNull private Integer publishedVersion;
        @NotBlankIdempotency private String idempotencyKey;
    }
    @Data public static class PublishReq {
        @NotNull private Long id;
        @NotNull private Integer version;
        @NotBlankIdempotency private String idempotencyKey;
    }
}
