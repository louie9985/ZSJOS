package cn.iocoder.yudao.module.zsjos.controller.admin.bpm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo.ZsjosBpmBusinessTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.service.bpm.ZsjosBpmBusinessTaskTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ZSJOS BPM 业务任务定位")
@RestController
@RequestMapping("/zsjos/bpm")
public class ZsjosBpmBusinessTaskTargetController {

    @Resource private ZsjosBpmBusinessTaskTargetService targetService;

    @GetMapping("/business-task-target")
    @Operation(summary = "定位 BPM 任务对应的员工端业务页")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<ZsjosBpmBusinessTaskTargetRespVO> getBusinessTaskTarget(
            @RequestParam @NotBlank String taskId,
            @RequestParam(defaultValue = "todo") String view) {
        return success(targetService.getTarget(taskId, view, WebFrameworkUtils.getLoginUserId()));
    }
}
