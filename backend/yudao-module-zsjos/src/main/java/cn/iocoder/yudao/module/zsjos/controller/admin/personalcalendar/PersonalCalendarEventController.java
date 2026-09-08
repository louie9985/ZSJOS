package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventListReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.personalcalendar.PersonalCalendarEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 我的日历")
@RestController
@RequestMapping("/zsjos/personal-calendar")
@Validated
public class PersonalCalendarEventController {
    @Resource private PersonalCalendarEventService service;

    @GetMapping
    @Operation(summary = "查询我的日程")
    @PreAuthorize("@ss.hasPermission('zsjos:personal-calendar:query')")
    public CommonResult<List<PersonalCalendarEventRespVO>> list(@Valid PersonalCalendarEventListReqVO req) {
        return success(service.list(req, getLoginUserId()));
    }

    @PostMapping
    @Operation(summary = "创建个人日程")
    @PreAuthorize("@ss.hasPermission('zsjos:personal-calendar:create')")
    public CommonResult<Long> create(@Valid @RequestBody PersonalCalendarEventSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改个人日程")
    @PreAuthorize("@ss.hasPermission('zsjos:personal-calendar:update')")
    public CommonResult<Boolean> update(@PathVariable Long id,
                                        @Valid @RequestBody PersonalCalendarEventSaveReqVO req) {
        service.update(id, req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除个人日程")
    @PreAuthorize("@ss.hasPermission('zsjos:personal-calendar:delete')")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        service.delete(id, getLoginUserId());
        return success(true);
    }
}
