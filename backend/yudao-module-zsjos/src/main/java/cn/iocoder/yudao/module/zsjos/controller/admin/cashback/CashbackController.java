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
    @Resource private cn.iocoder.yudao.module.zsjos.service.cashback.CashbackControlService control;

    @PutMapping("/{id}/block")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query') && @ss.hasPermission('zsjos:cashback:block')")
    public CommonResult<Boolean> block(@PathVariable Long id, @Valid @RequestBody cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackControlReqVO request) {
        control.block(id, request); return success(true);
    }
    @PutMapping("/{id}/unblock")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query') && @ss.hasPermission('zsjos:cashback:unblock')")
    public CommonResult<Boolean> unblock(@PathVariable Long id, @Valid @RequestBody cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackControlReqVO request) {
        control.unblock(id, request); return success(true);
    }
    @GetMapping("/{id}/control-history")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query')")
    public CommonResult<PageResult<cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackControlLogRespVO>> controlHistory(
            @PathVariable Long id, @Valid cn.iocoder.yudao.framework.common.pojo.PageParam page) {
        return success(control.history(id, page));
    }

    @Resource private cn.iocoder.yudao.module.zsjos.service.cashback.FinanceTraceService traceService;

    @PostMapping("/search-page")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query')")
    public CommonResult<PageResult<CashbackRespVO>> searchPage(@Valid @RequestBody CashbackPageReqVO request) {
        return success(service.getFinancePage(request));
    }

    @PostMapping("/my-search-page")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:my-query')")
    public CommonResult<PageResult<CashbackRespVO>> mySearchPage(@Valid @RequestBody CashbackPageReqVO request) {
        return success(service.getPage(request, WebFrameworkUtils.getLoginUserId()));
    }



    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:my-query')")
    public CommonResult<PageResult<CashbackRespVO>> myPage(@Valid CashbackPageReqVO request) {
        return success(service.getPage(request, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query')")
    public CommonResult<PageResult<CashbackRespVO>> financePage(@Valid CashbackPageReqVO request) {
        return success(service.getFinancePage(request));
    }
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query')")
    public CommonResult<CashbackRespVO> detail(@PathVariable Long id) {
        return success(traceService.detail(id));
    }
    @GetMapping("/{id}/withdrawals")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query') && @ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<PageResult<cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackWithdrawalRespVO>> history(
            @PathVariable Long id, @Valid cn.iocoder.yudao.framework.common.pojo.PageParam page) {
        return success(traceService.history(id, page));
    }
}
