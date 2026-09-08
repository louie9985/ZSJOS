package cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.ClassTransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "员工工作台 - 班级调班")
@RestController
@RequestMapping("/zsjos/class-transfer")
public class ClassTransferController {
    @Resource private ClassTransferService service;

    @PostMapping("/service/{relationId}")
    @PreAuthorize("@ss.hasPermission('zsjos:class-transfer:create')")
    public CommonResult<Long> create(@PathVariable Long relationId,
            @Valid @RequestBody ClassTransferCreateReqVO req) {
        return success(service.create(relationId, req, getLoginUserId()));
    }

    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:class-transfer:query')")
    public CommonResult<PageResult<ClassTransferRespVO>> myPage(@Valid ClassTransferPageReqVO req) {
        return success(service.getMyPage(req, getLoginUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:class-transfer:query')")
    public CommonResult<ClassTransferRespVO> get(@PathVariable Long id) {
        return success(service.get(id, getLoginUserId()));
    }
}
