package cn.iocoder.yudao.module.zsjos.controller.admin.calendar;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.*;
import cn.iocoder.yudao.module.zsjos.service.calendar.CalendarPaletteService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/zsjos/calendar-colors")
public class CalendarPaletteController {
    @Resource private CalendarPaletteService service;
    @GetMapping("/{type}")
    @PreAuthorize("@ss.hasPermission(T(cn.iocoder.yudao.module.zsjos.service.calendar.CalendarPalettePermissionProvider).permission(#type, false))")
    public CommonResult<CalendarPaletteRespVO> get(@PathVariable String type) { return success(service.get(type,getLoginUserId())); }
    @PostMapping("/{type}")
    @PreAuthorize("@ss.hasPermission(T(cn.iocoder.yudao.module.zsjos.service.calendar.CalendarPalettePermissionProvider).permission(#type, true))")
    public CommonResult<CalendarPaletteRespVO> create(@PathVariable String type, @Valid @RequestBody CalendarColorSaveReqVO request) { return success(service.mutate(type,getLoginUserId(),null,request,false)); }
    @PutMapping("/{type}/{id}")
    @PreAuthorize("@ss.hasPermission(T(cn.iocoder.yudao.module.zsjos.service.calendar.CalendarPalettePermissionProvider).permission(#type, true))")
    public CommonResult<CalendarPaletteRespVO> update(@PathVariable String type, @PathVariable String id, @Valid @RequestBody CalendarColorSaveReqVO request) { return success(service.mutate(type,getLoginUserId(),id,request,false)); }
    @DeleteMapping("/{type}/{id}")
    @PreAuthorize("@ss.hasPermission(T(cn.iocoder.yudao.module.zsjos.service.calendar.CalendarPalettePermissionProvider).permission(#type, true))")
    public CommonResult<CalendarPaletteRespVO> delete(@PathVariable String type, @PathVariable String id) { return success(service.mutate(type,getLoginUserId(),id,null,true)); }
}
