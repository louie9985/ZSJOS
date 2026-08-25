package cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen.vo.MediaScreenRespVO;
import cn.iocoder.yudao.module.zsjos.service.mediascreen.MediaScreenQueryService;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/media-screen")
@Validated
public class MediaScreenController {
    private final MediaScreenQueryService service;
    private final cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties properties;
    public MediaScreenController(MediaScreenQueryService service, cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties properties) { this.service = service; this.properties = properties; }

    @GetMapping("/stats")
    public CommonResult<MediaScreenRespVO> stats(@RequestParam @NotNull Long tenantId,
                                                  @RequestParam(defaultValue = "0") int includePartTimers) {
        validateFlag(includePartTimers); return success(service.stats(tenantId, includePartTimers == 1));
    }

    @GetMapping("/history")
    public CommonResult<MediaScreenRespVO> history(@RequestParam @NotNull Long tenantId,
                                                    @RequestParam(required = false) LocalDate date,
                                                    @RequestParam(defaultValue = "0") int includePartTimers) {
        validateFlag(includePartTimers); LocalDate requested = date == null ? LocalDate.now() : date;
        if (requested.isAfter(LocalDate.now()) || requested.isBefore(LocalDate.now().minusDays(properties.getLimits().getMaxHistoryDays()))) throw new IllegalArgumentException("历史日期超出允许范围");
        return success(service.history(tenantId, requested, includePartTimers == 1));
    }

    @GetMapping("/maintenance/status")
    public CommonResult<Map<String, Object>> maintenance(@RequestParam @NotNull Long tenantId) {
        return success(service.maintenance(tenantId));
    }
    private static void validateFlag(int value) { if (value != 0 && value != 1) throw new IllegalArgumentException("includePartTimers 只能为 0 或 1"); }
}
