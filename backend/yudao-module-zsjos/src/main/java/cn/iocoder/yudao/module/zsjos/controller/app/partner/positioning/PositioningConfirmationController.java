package cn.iocoder.yudao.module.zsjos.controller.app.partner.positioning;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.app.positioning.vo.PositioningConfirmReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.positioning.vo.PositioningConfirmationRespVO;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningConfirmationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** Partner H5 positioning confirmation endpoints; package placement selects the /part-api prefix. */
@RestController
@RequestMapping("/zsjos/positioning-confirmation")
public class PositioningConfirmationController {

    @Resource
    private PositioningConfirmationService service;

    @GetMapping("/{id}")
    public CommonResult<PositioningConfirmationRespVO> get(@PathVariable Long id) {
        return success(service.detail(id, getLoginUserId()));
    }

    @PostMapping("/{id}/confirm")
    public CommonResult<Boolean> confirm(@PathVariable Long id,
                                         @Valid @RequestBody PositioningConfirmReqVO req) {
        service.confirm(id, req.getVersion(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/reject")
    public CommonResult<Boolean> reject(@PathVariable Long id,
                                        @Valid @RequestBody PositioningConfirmReqVO req) {
        service.reject(id, req.getVersion(), getLoginUserId());
        return success(true);
    }
}
