package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOpenRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_OPEN_REQUEST_STATUS_APPROVED;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_OPEN_REQUEST_STATUS_PENDING;

@Mapper
public interface PartnerOpenRequestMapper extends BaseMapperX<PartnerOpenRequestDO> {

    default PageResult<PartnerOpenRequestDO> selectPage(PartnerOpenRequestPageReqVO reqVO, Long applicantUserId) {
        LambdaQueryWrapperX<PartnerOpenRequestDO> query = new LambdaQueryWrapperX<>();
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(group -> group.like(PartnerOpenRequestDO::getRequestNo, reqVO.getKeyword())
                    .or().like(PartnerOpenRequestDO::getPartnerName, reqVO.getKeyword())
                    .or().like(PartnerOpenRequestDO::getPartnerMobile, reqVO.getKeyword())
                    .or().like(PartnerOpenRequestDO::getInviteCodeSnapshot, reqVO.getKeyword()));
        }
        return selectPage(reqVO, query.eqIfPresent(PartnerOpenRequestDO::getApplicantUserId, applicantUserId)
                .eqIfPresent(PartnerOpenRequestDO::getStatus, reqVO.getStatus())
                .orderByDesc(PartnerOpenRequestDO::getSubmittedAt)
                .orderByDesc(PartnerOpenRequestDO::getId));
    }

    @Select("SELECT * FROM zsjos_partner_open_request WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    PartnerOpenRequestDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PartnerOpenRequestDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(PartnerOpenRequestDO::getProcessInstanceId, processInstanceId);
    }

    default PartnerOpenRequestDO selectByApplicantAndIdempotencyKey(Long applicantUserId, String idempotencyKey,
                                                                    Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<PartnerOpenRequestDO>()
                .eq(PartnerOpenRequestDO::getTenantId, tenantId)
                .eq(PartnerOpenRequestDO::getApplicantUserId, applicantUserId)
                .eq(PartnerOpenRequestDO::getIdempotencyKey, idempotencyKey));
    }

    default PartnerOpenRequestDO selectActiveByMobileForUpdate(String mobile, Long tenantId) {
        return selectFirstOneForUpdate(new LambdaQueryWrapperX<PartnerOpenRequestDO>()
                .eq(PartnerOpenRequestDO::getTenantId, tenantId)
                .eq(PartnerOpenRequestDO::getActiveMobileKey, mobile)
                .in(PartnerOpenRequestDO::getStatus, List.of(PARTNER_OPEN_REQUEST_STATUS_PENDING,
                        PARTNER_OPEN_REQUEST_STATUS_APPROVED))
                .orderByDesc(PartnerOpenRequestDO::getId));
    }
}
