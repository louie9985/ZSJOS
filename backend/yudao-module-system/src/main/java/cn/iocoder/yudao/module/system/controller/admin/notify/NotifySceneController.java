package cn.iocoder.yudao.module.system.controller.admin.notify;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.service.notify.NotifySceneRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 业务通知场景")
@RestController
@RequestMapping("/system/notify-scene")
public class NotifySceneController {

    @Resource
    private NotifySceneRegistry sceneRegistry;

    @GetMapping("/list")
    @Operation(summary = "获得业务通知场景目录")
    @PreAuthorize("@ss.hasPermission('system:notify-rule:query')")
    public CommonResult<List<NotifySceneRespDTO>> getSceneList() {
        return success(sceneRegistry.getScenes());
    }
}
