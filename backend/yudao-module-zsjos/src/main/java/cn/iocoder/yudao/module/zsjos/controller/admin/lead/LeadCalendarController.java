package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadCalendarService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@Validated
@RequestMapping("/zsjos/lead-follow-up-calendar")
@Tag(name = "管理后台 - 销售客资跟进日历")
public class LeadCalendarController {
    @Resource private LeadCalendarService service;

    @GetMapping("/days")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up-calendar:query') && @ss.hasPermission('zsjos:lead:query')")
    public CommonResult<List<LeadCalendarDayRespVO>> days(@Valid LeadCalendarQueryReqVO query) {
        return success(service.days(query, getLoginUserId()));
    }

    @GetMapping("/cards")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up-calendar:query') && @ss.hasPermission('zsjos:lead:query')")
    public CommonResult<PageResult<LeadCalendarCardRespVO>> cards(@Valid LeadCalendarQueryReqVO query) {
        return success(service.cards(query, getLoginUserId()));
    }
}
