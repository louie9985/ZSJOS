package cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar.PersonalCalendarEventDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PersonalCalendarEventMapper extends BaseMapperX<PersonalCalendarEventDO> {
    default List<PersonalCalendarEventDO> selectMyRange(Long ownerUserId, LocalDateTime start, LocalDateTime end) {
        java.util.Objects.requireNonNull(ownerUserId);
        return selectReadRange(ownerUserId, start, end);
    }

    default List<PersonalCalendarEventDO> selectReadRange(Long ownerUserId, LocalDateTime start, LocalDateTime end) {
        return selectList(new LambdaQueryWrapperX<PersonalCalendarEventDO>()
                .eqIfPresent(PersonalCalendarEventDO::getOwnerUserId, ownerUserId)
                // Normal events intersect the half-open day range; zero-length events belong to their point day.
                .and(wrapper -> wrapper
                        .and(normal -> normal.lt(PersonalCalendarEventDO::getStartTime, end)
                                .gt(PersonalCalendarEventDO::getEndTime, start))
                        .or(point -> point.apply("start_time = end_time")
                                .ge(PersonalCalendarEventDO::getStartTime, start)
                                .lt(PersonalCalendarEventDO::getStartTime, end)))
                .orderByAsc(PersonalCalendarEventDO::getStartTime)
                .orderByAsc(PersonalCalendarEventDO::getId));
    }

    default int deleteOwned(Long id, Long ownerUserId, LocalDateTime deletedTime) {
        return update(null, new LambdaUpdateWrapper<PersonalCalendarEventDO>()
                .eq(PersonalCalendarEventDO::getId, id)
                .eq(PersonalCalendarEventDO::getOwnerUserId, ownerUserId)
                .set(PersonalCalendarEventDO::getDeleted, true)
                .set(PersonalCalendarEventDO::getDeletedTime, deletedTime));
    }

    default int updateOwned(PersonalCalendarEventDO event, Long ownerUserId) {
        return update(event, new LambdaUpdateWrapper<PersonalCalendarEventDO>()
                .eq(PersonalCalendarEventDO::getId, event.getId())
                .eq(PersonalCalendarEventDO::getOwnerUserId, ownerUserId));
    }
}
