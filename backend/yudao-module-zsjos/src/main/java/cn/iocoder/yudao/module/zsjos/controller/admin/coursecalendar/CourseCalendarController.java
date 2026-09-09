package cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseCalendarEventService;
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

@Tag(name="管理后台 - 课程日历")
@RestController @RequestMapping("/zsjos/course-calendar") @Validated
public class CourseCalendarController {
    @Resource private CourseCalendarEventService service;
    @GetMapping("/page") @Operation(summary="查询课程日历") @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:query')")
    public CommonResult<List<CourseCalendarRespVO>> page(@Valid CourseCalendarPageReqVO req) { return success(service.list(req)); }
    @GetMapping("/{id}") @Operation(summary="查询课程详情") @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:query')")
    public CommonResult<CourseCalendarRespVO> get(@PathVariable Long id) { return success(service.get(id)); }
    @PostMapping @Operation(summary="新增课程安排") @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:manage')")
    public CommonResult<Long> create(@Valid @RequestBody CourseCalendarSaveReqVO req) { return success(service.create(req)); }
    @PutMapping("/{id}") @Operation(summary="修改课程安排") @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:manage')")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody CourseCalendarSaveReqVO req) { service.update(id, req); return success(true); }
    @DeleteMapping("/{id}") @Operation(summary="删除课程安排") @PreAuthorize("@ss.hasPermission('zsjos:course-calendar:manage')")
    public CommonResult<Boolean> delete(@PathVariable Long id) { service.delete(id); return success(true); }
}
