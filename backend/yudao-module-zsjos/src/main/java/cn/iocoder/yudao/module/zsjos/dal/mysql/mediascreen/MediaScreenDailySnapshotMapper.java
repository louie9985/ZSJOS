package cn.iocoder.yudao.module.zsjos.dal.mysql.mediascreen;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.mediascreen.MediaScreenDailySnapshotDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MediaScreenDailySnapshotMapper extends BaseMapperX<MediaScreenDailySnapshotDO> {
    @Insert("INSERT IGNORE INTO zsjos_media_screen_daily_snapshot(snapshot_date,contribution_type,department_id,department_name,supervisor_id,supervisor_name,member_id,member_name,member_enabled,today_count,week_count,month_total,month_effective,partner_details_json,creator,create_time,updater,update_time,deleted,tenant_id) "
            + "VALUES(#{row.snapshotDate},#{row.contributionType},#{row.departmentId},#{row.departmentName},#{row.supervisorId},#{row.supervisorName},#{row.memberId},#{row.memberName},#{row.memberEnabled},#{row.todayCount},#{row.weekCount},#{row.monthTotal},#{row.monthEffective},#{row.partnerDetailsJson},'',NOW(),'',NOW(),b'0',#{tenantId})")
    int insertIgnore(@Param("tenantId") Long tenantId, @Param("row") MediaScreenDailySnapshotDO row);

    default List<MediaScreenDailySnapshotDO> selectByDate(Long tenantId, LocalDate date) {
        return selectList(new LambdaQueryWrapperX<MediaScreenDailySnapshotDO>()
                .eq(MediaScreenDailySnapshotDO::getTenantId, tenantId)
                .eq(MediaScreenDailySnapshotDO::getSnapshotDate, date));
    }

    default List<MediaScreenDailySnapshotDO> selectByDateBetween(Long tenantId, LocalDate from, LocalDate to) {
        return selectList(new LambdaQueryWrapperX<MediaScreenDailySnapshotDO>()
                .eq(MediaScreenDailySnapshotDO::getTenantId, tenantId)
                .between(MediaScreenDailySnapshotDO::getSnapshotDate, from, to));
    }

    default List<Long> selectMemberIds(Long tenantId, LocalDate date, Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<MediaScreenDailySnapshotDO>()
                .select(MediaScreenDailySnapshotDO::getMemberId)
                .eq(MediaScreenDailySnapshotDO::getTenantId, tenantId)
                .eq(MediaScreenDailySnapshotDO::getSnapshotDate, date)
                .in(MediaScreenDailySnapshotDO::getMemberId, memberIds))
                .stream().map(MediaScreenDailySnapshotDO::getMemberId).toList();
    }
}
