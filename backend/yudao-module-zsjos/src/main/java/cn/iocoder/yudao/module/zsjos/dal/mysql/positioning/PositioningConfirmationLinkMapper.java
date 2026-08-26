package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningConfirmationLinkDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PositioningConfirmationLinkMapper extends BaseMapperX<PositioningConfirmationLinkDO> {
    @TenantIgnore
    default PositioningConfirmationLinkDO selectByTokenHash(String tokenHash) {
        return selectOne(new LambdaQueryWrapperX<PositioningConfirmationLinkDO>()
                .eq(PositioningConfirmationLinkDO::getTokenHash, tokenHash).last("LIMIT 1"));
    }

    @TenantIgnore
    @Select("SELECT * FROM zsjos_positioning_confirmation_link WHERE token_hash=#{tokenHash} "
            + "AND deleted=b'0' FOR UPDATE")
    PositioningConfirmationLinkDO selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    default int revokeActiveBySubmission(Long submissionId, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<PositioningConfirmationLinkDO>()
                .eq(PositioningConfirmationLinkDO::getSubmissionId, submissionId)
                .eq(PositioningConfirmationLinkDO::getStatus, "active")
                .set(PositioningConfirmationLinkDO::getStatus, "revoked")
                .set(PositioningConfirmationLinkDO::getRevokedAt, now)
                .setSql("version = version + 1"));
    }

    default int consume(Long id, Integer version, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<PositioningConfirmationLinkDO>()
                .eq(PositioningConfirmationLinkDO::getId, id)
                .eq(PositioningConfirmationLinkDO::getVersion, version)
                .eq(PositioningConfirmationLinkDO::getStatus, "active")
                .gt(PositioningConfirmationLinkDO::getExpiresAt, now)
                .set(PositioningConfirmationLinkDO::getStatus, "used")
                .set(PositioningConfirmationLinkDO::getUsedAt, now)
                .set(PositioningConfirmationLinkDO::getVersion, version + 1));
    }
}
