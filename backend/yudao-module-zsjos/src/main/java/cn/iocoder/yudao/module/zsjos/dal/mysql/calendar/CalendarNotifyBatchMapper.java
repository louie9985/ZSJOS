package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyBatchDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchPageReqVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CalendarNotifyBatchMapper extends BaseMapperX<CalendarNotifyBatchDO> {
    default PageResult<CalendarNotifyBatchDO> selectHistoryPage(CalendarNotifyBatchPageReqVO req, Long tenantId) {
        // All employees already see calendars; notification history is bounded by tenant and authorized calendar type.
        return selectPage(req, new LambdaQueryWrapperX<CalendarNotifyBatchDO>()
                .eq(CalendarNotifyBatchDO::getTenantId, tenantId)
                .eq(CalendarNotifyBatchDO::getCalendarType, req.getCalendarType())
                .eqIfPresent(CalendarNotifyBatchDO::getCalendarId, req.getCalendarId())
                .eqIfPresent(CalendarNotifyBatchDO::getStatus, req.getStatus())
                .orderByDesc(CalendarNotifyBatchDO::getId));
    }
    default CalendarNotifyBatchDO selectIdempotencyKey(String key) {
        return selectOne(CalendarNotifyBatchDO::getIdempotencyKey, key);
    }
}
