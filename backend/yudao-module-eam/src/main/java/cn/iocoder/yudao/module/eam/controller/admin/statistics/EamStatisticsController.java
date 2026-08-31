package cn.iocoder.yudao.module.eam.controller.admin.statistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.eam.controller.admin.statistics.vo.EamStatisticsRespVO;
import cn.iocoder.yudao.module.eam.service.statistics.EamStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - EAM 资产统计")
@RestController
@RequestMapping("/eam/statistics")
@Validated
public class EamStatisticsController {

    @Resource
    private EamStatisticsService statisticsService;

    @GetMapping("/overview")
    @Operation(summary = "获得资产统计概览")
    @PreAuthorize("@ss.hasPermission('eam:statistics:query')")
    public CommonResult<EamStatisticsRespVO> getStatistics() {
        return success(statisticsService.getStatistics());
    }

}
