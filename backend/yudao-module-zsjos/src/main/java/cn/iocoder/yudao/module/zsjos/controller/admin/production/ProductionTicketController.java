package cn.iocoder.yudao.module.zsjos.controller.admin.production;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketActionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketCreateContextRespVO;
import cn.iocoder.yudao.module.zsjos.service.production.ProductionTicketService;
import cn.iocoder.yudao.module.zsjos.service.production.ProductionTicketActionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import java.util.List;

@RestController
@RequestMapping("/zsjos/production-ticket")
public class ProductionTicketController {
    @Resource private ProductionTicketService service;
    @Resource private ProductionTicketActionService actionService;

    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductionTicketSaveReqVO req) { return success(service.create(req, getLoginUserId())); }

    @GetMapping("/create-context") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:create')")
    public CommonResult<ProductionTicketCreateContextRespVO> createContext(@RequestParam Long accountId,
                                                                            @RequestParam String sceneCode) {
        return success(service.getCreateContext(accountId, sceneCode, getLoginUserId()));
    }

    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:query')")
    public CommonResult<ProductionTicketRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }

    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:query')")
    public CommonResult<PageResult<ProductionTicketRespVO>> page(@Valid ProductionTicketPageReqVO req) { return success(service.page(req, getLoginUserId())); }

    @GetMapping("/pool/page") @PreAuthorize("@ss.hasAnyPermissions('zsjos:production-ticket:pool-query','zsjos:production-ticket:claim')")
    public CommonResult<PageResult<ProductionTicketRespVO>> poolPage(@Valid ProductionTicketPageReqVO req) {
        return success(service.poolPage(req, getLoginUserId()));
    }

    @GetMapping("/assignment/my-pending") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:accept')")
    public CommonResult<List<ProductionTicketRespVO>> myPending() {
        return success(service.myPending(getLoginUserId()));
    }

    @PostMapping("/{id}/accept") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:accept')")
    public CommonResult<Boolean> accept(@PathVariable Long id, @RequestParam Integer version) { service.accept(id, version); return success(true); }

    @PostMapping("/{id}/reject-assignment")
    @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:reject-assignment')")
    public CommonResult<Boolean> rejectAssignment(@PathVariable Long id,
                                                   @Valid @RequestBody ProductionTicketActionReqVO req) {
        return success(actionService.rejectAssignment(id, req.getVersion(), req.getReason(),
                req.getIdempotencyKey(), getLoginUserId()));
    }

    @PostMapping("/{id}/claim") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:claim')")
    public CommonResult<Boolean> claim(@PathVariable Long id, @Valid @RequestBody ProductionTicketActionReqVO req) {
        return success(actionService.claim(id, req.getVersion(), req.getIdempotencyKey(), getLoginUserId()));
    }

    @PostMapping("/{id}/start-production") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:produce')")
    public CommonResult<Boolean> startProduction(@PathVariable Long id, @RequestParam Integer version) { service.startProduction(id, version); return success(true); }

    @PostMapping("/{id}/submit") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:submit')")
    public CommonResult<Boolean> submit(@PathVariable Long id, @RequestParam Integer version) { service.submit(id, version); return success(true); }

    @PostMapping("/{id}/start-check") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:check')")
    public CommonResult<Boolean> startCheck(@PathVariable Long id, @RequestParam Integer version) { service.startCheck(id, version); return success(true); }

    @PostMapping("/{id}/approve") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:check')")
    public CommonResult<Boolean> approve(@PathVariable Long id, @RequestParam Integer version) { service.approve(id, version); return success(true); }

    @PostMapping("/{id}/reject") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:check')")
    public CommonResult<Boolean> reject(@PathVariable Long id, @RequestParam Integer version,
                                        @RequestParam String reason) {
        service.reject(id, version, reason);
        return success(true);
    }

    @PostMapping("/{id}/reaccept") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:accept')")
    public CommonResult<Boolean> reaccept(@PathVariable Long id, @RequestParam Integer version) { service.reaccept(id, version); return success(true); }
}
