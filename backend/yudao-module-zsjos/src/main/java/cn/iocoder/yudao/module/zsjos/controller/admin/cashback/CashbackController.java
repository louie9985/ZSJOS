package cn.iocoder.yudao.module.zsjos.controller.admin.cashback;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/cashback")
public class CashbackController {
    @Resource private CashbackService service;

    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:my-query')")
    public CommonResult<PageResult<CashbackRespVO>> myPage(@Valid CashbackPageReqVO request) {
        return success(service.getPage(request, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query')")
    public CommonResult<PageResult<CashbackRespVO>> financePage(@Valid CashbackPageReqVO request) {
        return success(service.getPage(request, null));
    }
}
