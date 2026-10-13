package cn.iocoder.yudao.module.zsjos.service.calendar;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarPaletteRespVO.Color;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarPaletteDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarPaletteMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
@Service
public class CalendarPaletteService {
    @Resource private CalendarPaletteMapper mapper;
    @Resource private CalendarPalettePermissionProvider access;
    // COURSE_SHARED/0 is a storage scope, never an employee identity. V315 initializes it once.
    private static Long owner(String type, Long user) { return "COURSE".equals(type) ? 0L : user; }
    private static String scope(String type) { return "COURSE".equals(type) ? "COURSE_SHARED" : type; }
    @ZsjosPermission(bizType="calendar-palette", bizId="#user", action="read")
    public CalendarPaletteRespVO get(String type, Long user) {
        access.check(user, type + ":read", user);
        var row = mapper.selectOne(new LambdaQueryWrapper<CalendarPaletteDO>()
            .eq(CalendarPaletteDO::getTenantId, TenantContextHolder.getRequiredTenantId())
            .eq(CalendarPaletteDO::getUserId, owner(type,user)).eq(CalendarPaletteDO::getCalendarType, scope(type)));
        return response(type, row);
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="calendar-palette", bizId="#user", action="write")
    public CalendarPaletteRespVO mutate(String type, Long user, String id, CalendarColorSaveReqVO request, boolean delete) {
        access.check(user, type + ":write", user);
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (!"COURSE".equals(type)) mapper.ensure(tenant, user, type);
        var row = mapper.lock(tenant, owner(type,user), scope(type));
        if (row == null) throw exception("COURSE".equals(type) ? CalendarColors.NOT_INITIALIZED : CalendarColors.DENIED);
        var colors = new ArrayList<>(response(type, row).colors());
        int index = -1;
        for (int i=0; i<colors.size(); i++) if (colors.get(i).id().equals(id)) index=i;
        if (id != null && index < 0) throw exception(CalendarColors.MISSING);
        if (delete) colors.remove(index);
        else {
            String name = request.getName().strip();
            if (name.isEmpty() || name.codePointCount(0,name.length()) > 20 || colors.stream().anyMatch(c -> !c.id().equals(id) && c.name().equalsIgnoreCase(name))) throw exception(CalendarColors.NAME);
            String color = CalendarColors.resolve(request.getColor(), null);
            if (color == null) throw exception(CalendarColors.INVALID);
            if (id == null && colors.size() >= 20) throw exception(CalendarColors.LIMIT);
            var item = new Color(id == null ? UUID.randomUUID().toString() : id, name, color);
            if (index < 0) colors.add(item); else colors.set(index,item);
        }
        row.setColorsJson(JsonUtils.toJsonString(colors));
        mapper.updateById(row);
        return response(type,row);
    }
    private CalendarPaletteRespVO response(String type, CalendarPaletteDO row) {
        if ("COURSE".equals(type) && row == null) throw exception(CalendarColors.NOT_INITIALIZED);
        return new CalendarPaletteRespVO(List.of(), row == null ? List.of() : JsonUtils.parseArray(row.getColorsJson(), Color.class));
    }
}
