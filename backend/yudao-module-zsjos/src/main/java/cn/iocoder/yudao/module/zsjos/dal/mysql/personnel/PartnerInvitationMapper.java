package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerInvitationDO;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_ACTIVE;

@Mapper
public interface PartnerInvitationMapper extends BaseMapperX<PartnerInvitationDO> {

    default PageResult<PartnerInvitationDO> selectPage(PartnerInvitationPageReqVO reqVO) {
        LambdaQueryWrapperX<PartnerInvitationDO> query = new LambdaQueryWrapperX<>();
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(group -> group.like(PartnerInvitationDO::getName, reqVO.getKeyword())
                    .or().like(PartnerInvitationDO::getMobile, reqVO.getKeyword())
                    .or().like(PartnerInvitationDO::getInviteCode, reqVO.getKeyword()));
        }
        return selectPage(reqVO, query.eqIfPresent(PartnerInvitationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(PartnerInvitationDO::getAssignedOperatorUserId, reqVO.getAssignedOperatorUserId())
                .orderByDesc(PartnerInvitationDO::getId));
    }

    default PartnerInvitationDO selectByInviteCode(String inviteCode) {
        return selectOne(PartnerInvitationDO::getInviteCode, inviteCode);
    }

    default PartnerInvitationDO selectActiveByMobileAndCodeForUpdate(String mobile, String inviteCode) {
        return selectFirstOneForUpdate(new LambdaQueryWrapperX<PartnerInvitationDO>()
                .eq(PartnerInvitationDO::getMobile, mobile)
                .eq(PartnerInvitationDO::getInviteCode, inviteCode)
                .eq(PartnerInvitationDO::getStatus, PARTNER_INVITATION_STATUS_ACTIVE)
                .orderByDesc(PartnerInvitationDO::getId));
    }

    default PartnerInvitationDO selectLatestByMobileAndCodeForUpdate(String mobile, String inviteCode) {
        return selectFirstOneForUpdate(new LambdaQueryWrapperX<PartnerInvitationDO>()
                .eq(PartnerInvitationDO::getMobile, mobile)
                .eq(PartnerInvitationDO::getInviteCode, inviteCode)
                .orderByDesc(PartnerInvitationDO::getId));
    }

    default boolean hasActiveByMobile(String mobile, LocalDateTime now) {
        return selectCount(new LambdaQueryWrapperX<PartnerInvitationDO>()
                .eq(PartnerInvitationDO::getMobile, mobile)
                .eq(PartnerInvitationDO::getStatus, PARTNER_INVITATION_STATUS_ACTIVE)
                .gt(PartnerInvitationDO::getExpiresAt, now)) > 0;
    }

    default int voidActiveByMobile(String mobile, LocalDateTime voidedAt) {
        return update(null, new LambdaUpdateWrapper<PartnerInvitationDO>()
                .eq(PartnerInvitationDO::getMobile, mobile)
                .eq(PartnerInvitationDO::getStatus, PARTNER_INVITATION_STATUS_ACTIVE)
                .set(PartnerInvitationDO::getStatus, cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_VOIDED)
                .set(PartnerInvitationDO::getVoidedAt, voidedAt)
                .setSql("version = version + 1"));
    }
}
