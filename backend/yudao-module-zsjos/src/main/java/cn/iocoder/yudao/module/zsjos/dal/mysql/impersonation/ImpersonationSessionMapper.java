package cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation.ImpersonationSessionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ImpersonationSessionMapper extends BaseMapperX<ImpersonationSessionDO> {
    default ImpersonationSessionDO selectActive(Long id, Long administratorUserId) {
        return selectOne(new LambdaQueryWrapperX<ImpersonationSessionDO>().eq(ImpersonationSessionDO::getId, id)
                .eq(ImpersonationSessionDO::getAdministratorUserId, administratorUserId)
                .eq(ImpersonationSessionDO::getStatus, "active"));
    }

    default List<ImpersonationSessionDO> selectIdle(LocalDateTime cutoff) {
        return selectList(new LambdaQueryWrapperX<ImpersonationSessionDO>()
                .eq(ImpersonationSessionDO::getStatus, "active")
                .le(ImpersonationSessionDO::getLastActiveAt, cutoff).last("LIMIT 200"));
    }

    default int touch(Long id, Integer version, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<ImpersonationSessionDO>()
                .eq(ImpersonationSessionDO::getId, id).eq(ImpersonationSessionDO::getStatus, "active")
                .eq(ImpersonationSessionDO::getVersion, version)
                .set(ImpersonationSessionDO::getLastActiveAt, now)
                .set(ImpersonationSessionDO::getVersion, version + 1));
    }

    default int close(Long id, Integer version, String status, LocalDateTime endedAt, String endedReason) {
        return update(null, new LambdaUpdateWrapper<ImpersonationSessionDO>()
                .eq(ImpersonationSessionDO::getId, id).eq(ImpersonationSessionDO::getStatus, "active")
                .eq(ImpersonationSessionDO::getVersion, version)
                .set(ImpersonationSessionDO::getStatus, status)
                .set(ImpersonationSessionDO::getEndedAt, endedAt)
                .set(ImpersonationSessionDO::getEndedReason, endedReason)
                .set(ImpersonationSessionDO::getVersion, version + 1));
    }
}
