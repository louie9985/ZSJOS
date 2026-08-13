package cn.iocoder.yudao.module.zsjos.dal.mysql.export;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.service.export.ExportTaskStatus.*;

@Mapper
public interface ExportTaskMapper extends BaseMapperX<ExportTaskDO> {
    default PageResult<ExportTaskDO> selectCreatorPage(PageParam page, Long creatorUserId, String exportType) {
        return selectPage(page, new LambdaQueryWrapperX<ExportTaskDO>()
                .eq(ExportTaskDO::getCreatorUserId, creatorUserId)
                .eqIfPresent(ExportTaskDO::getExportType, exportType)
                .orderByDesc(ExportTaskDO::getCreateTime));
    }

    @Select("""
            SELECT * FROM zsjos_export_task WHERE tenant_id=#{tenantId} AND deleted=b'0'
              AND ((status='queued' AND (next_attempt_at IS NULL OR next_attempt_at<=#{now}))
                OR (status IN ('prechecking','generating') AND lease_expires_at<=#{now}))
              ORDER BY COALESCE(next_attempt_at,create_time),id LIMIT 3
            """)
    List<ExportTaskDO> selectClaimCandidates(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);

    default int claim(Long id, Integer version, LocalDateTime leaseExpiresAt) {
        return update(null, new LambdaUpdateWrapper<ExportTaskDO>().eq(ExportTaskDO::getId, id)
                .eq(ExportTaskDO::getVersion, version)
                .in(ExportTaskDO::getStatus, List.of("queued", "prechecking", "generating"))
                .set(ExportTaskDO::getStatus, "prechecking")
                .set(ExportTaskDO::getLeaseExpiresAt, leaseExpiresAt)
                .setSql("attempt_count=attempt_count+1")
                .set(ExportTaskDO::getVersion, version + 1));
    }

    default List<ExportTaskDO> selectReadyExpired(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<ExportTaskDO>().eq(ExportTaskDO::getStatus, "ready")
                .le(ExportTaskDO::getExpiresAt, now).last("LIMIT 200"));
    }

    default int deleteInactiveTerminal(LocalDateTime inactiveBefore) {
        return delete(new LambdaQueryWrapperX<ExportTaskDO>()
                .in(ExportTaskDO::getStatus, List.of(READY, FAILED, CANCELLED, EXPIRED))
                .le(ExportTaskDO::getLastActiveAt, inactiveBefore));
    }

    default int transition(Long id, Integer version, List<String> allowedStatuses, ExportTaskDO values) {
        LambdaUpdateWrapper<ExportTaskDO> update = new LambdaUpdateWrapper<ExportTaskDO>()
                .eq(ExportTaskDO::getId, id).eq(ExportTaskDO::getVersion, version)
                .in(ExportTaskDO::getStatus, allowedStatuses);
        ExportTaskDO updateValues = new ExportTaskDO();
        org.springframework.beans.BeanUtils.copyProperties(values, updateValues);
        updateValues.setId(null).setVersion(version + 1);
        return update(updateValues, update);
    }
}
