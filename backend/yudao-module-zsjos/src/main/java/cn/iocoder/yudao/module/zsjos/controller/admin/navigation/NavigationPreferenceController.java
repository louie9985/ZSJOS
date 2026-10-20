package cn.iocoder.yudao.module.zsjos.controller.admin.navigation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.navigation.vo.NavigationPreferenceVO;
import cn.iocoder.yudao.module.zsjos.service.navigation.NavigationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 个人导航设置")
@RestController
@RequestMapping("/zsjos/navigation-preference")
@Validated
public class NavigationPreferenceController {
    @Resource private NavigationPreferenceService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:navigation-preference:query')")
    @Operation(summary = "读取本人当前客户端的导航顺序")
    public CommonResult<NavigationPreferenceVO> get(@RequestParam String client) {
        return success(service.get(client));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermission('zsjos:navigation-preference:query') and @ss.hasPermission('zsjos:navigation-preference:update')")
    @Operation(summary = "保存本人当前客户端的导航顺序")
    public CommonResult<Boolean> save(@RequestParam String client, @Valid @RequestBody NavigationPreferenceVO preference) {
        service.save(client, preference);
        return success(true);
    }
}
