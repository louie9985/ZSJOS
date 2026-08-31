package cn.iocoder.yudao.module.zsjos.controller.admin.production;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketItemRespVO;
import cn.iocoder.yudao.module.zsjos.service.production.ProductionTicketItemService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController @RequestMapping("/zsjos/production-tickets/items")
public class ProductionTicketItemController {
    @Resource private ProductionTicketItemService service;
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:query')") public CommonResult<List<ProductionTicketItemRespVO>> list(@RequestParam Long ticketId) { return success(service.list(ticketId, getLoginUserId())); }
    @PostMapping("/add") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:edit')") public CommonResult<Long> add(@RequestParam Long ticketId, @RequestParam Long contentId) { return success(service.add(ticketId, contentId, getLoginUserId())); }
    @PostMapping("/remove") @PreAuthorize("@ss.hasPermission('zsjos:production-ticket:edit')") public CommonResult<Boolean> remove(@RequestParam Long ticketId, @RequestParam Long contentId) { service.remove(ticketId, contentId, getLoginUserId()); return success(true); }
}
