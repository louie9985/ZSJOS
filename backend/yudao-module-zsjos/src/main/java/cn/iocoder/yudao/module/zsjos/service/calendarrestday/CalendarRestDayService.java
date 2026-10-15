package cn.iocoder.yudao.module.zsjos.service.calendarrestday;

import cn.iocoder.yudao.module.zsjos.controller.admin.calendarrestday.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendarrestday.CalendarRestDayDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendarrestday.CalendarRestDayMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.CalendarRestDayErrorCodes.*;

@Service
public class CalendarRestDayService {
    @Resource private CalendarRestDayMapper mapper;
    @Resource private CalendarRestDayPermissionProvider access;

    @ZsjosPermission(bizType = "calendar-rest-day", bizId = "#tenantId", action = "read")
    public List<CalendarRestDayRespVO> list(Long tenantId, Long userId, LocalDate startDate, LocalDate endDate) {
        access.check(tenantId, "read", userId);
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
            || ChronoUnit.DAYS.between(startDate, endDate) > 365) throw exception(INVALID);
        return mapper.selectList(new LambdaQueryWrapper<CalendarRestDayDO>()
            .eq(CalendarRestDayDO::getTenantId, tenantId)
            .between(CalendarRestDayDO::getDate, startDate, endDate)
            .orderByAsc(CalendarRestDayDO::getDate)).stream().map(this::response).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "calendar-rest-day", bizId = "#tenantId", action = "write")
    public List<CalendarRestDayRespVO> update(Long tenantId, Long userId, CalendarRestDayBatchReqVO request) {
        access.check(tenantId, "write", userId);
        var changes = request.getChanges();
        if (changes == null || changes.isEmpty() || changes.size() > 366) throw exception(INVALID);
        Set<LocalDate> dates = new HashSet<>();
        for (var change : changes) {
            if (change == null || change.getDate() == null || change.getRest() == null
                || change.getVersion() == null || change.getVersion() < 0 || !dates.add(change.getDate())) throw exception(INVALID);
        }
        List<CalendarRestDayDO> rows = new ArrayList<>();
        for (var change : changes.stream().sorted(Comparator.comparing(CalendarRestDayChangeVO::getDate)).toList()) {
            mapper.ensureRow(tenantId, change.getDate(), userId);
            var row = mapper.lock(tenantId, change.getDate());
            if (row == null || !Objects.equals(row.getTenantId(), tenantId) || Boolean.TRUE.equals(row.getDeleted())) throw exception(DENIED);
            if (!Objects.equals(row.getVersion(), change.getVersion())) {
                // A retry of an already applied command is safe only if the desired state still matches.
                if (Objects.equals(row.getRest(), change.getRest())) { rows.add(row); continue; }
                throw exception(CONFLICT);
            }
            if (!Objects.equals(row.getRest(), change.getRest())) {
                row.setRest(change.getRest());
                row.setVersion(row.getVersion() + 1);
                mapper.updateById(row);
            }
            rows.add(row);
        }
        return rows.stream().map(this::response).toList();
    }

    private CalendarRestDayRespVO response(CalendarRestDayDO row) {
        return new CalendarRestDayRespVO().setDate(row.getDate()).setRest(row.getRest()).setVersion(row.getVersion());
    }
}
