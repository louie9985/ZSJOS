package cn.iocoder.yudao.module.zsjos.controller.admin.director;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorConfigVO;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 编导时效配置")
@RestController
@RequestMapping("/zsjos/director-config")
@Validated
public class DirectorConfigController {
    @Resource private DirectorConfigService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:director-config:query')")
    public CommonResult<DirectorConfigVO.Resp> get() { return success(service.get()); }
    @PutMapping
    @PreAuthorize("@ss.hasPermission('zsjos:director-config:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DirectorConfigVO.UpdateReq req) { service.update(req); return success(true); }
}
