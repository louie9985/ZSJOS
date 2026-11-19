package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.TextbookAddressService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zsjos/gift-purchase/textbook-address")
public class TextbookAddressController {
    @Resource private TextbookAddressService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:query') && @ss.hasPermission('zsjos:sales-order:query-management') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PageResult<TextbookAddressService.Row>> page(@Valid PageParam page,
            @RequestParam(required = false) String keyword, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(service.page(page.getPageNo(), page.getPageSize(), keyword, WebFrameworkUtils.getLoginUserId()));
    }
}
