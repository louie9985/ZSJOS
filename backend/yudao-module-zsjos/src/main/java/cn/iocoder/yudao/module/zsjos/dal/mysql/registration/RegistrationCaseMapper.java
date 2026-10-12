package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationPoolPageReqVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Mapper
public interface RegistrationCaseMapper extends BaseMapperX<RegistrationCaseDO> {
    default RegistrationCaseDO selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<RegistrationCaseDO>().eq(RegistrationCaseDO::getOrderId, orderId));
    }

    @Select("SELECT * FROM zsjos_registration_case WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    RegistrationCaseDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<RegistrationCaseDO> selectPoolPage(RegistrationPoolPageReqVO pageParam,
                                                           java.util.List<Long> orderIds,
                                                           java.util.List<Long> caseIds) {
        LambdaQueryWrapperX<RegistrationCaseDO> query = new LambdaQueryWrapperX<RegistrationCaseDO>()
                .eqIfPresent(RegistrationCaseDO::getStatus, pageParam.getStatus());
        if ("pending".equals(pageParam.getStatusGroup())) {
            query.in(RegistrationCaseDO::getStatus, STATUS_PENDING, STATUS_PROCESSING);
        } else if ("processed".equals(pageParam.getStatusGroup())) {
            query.in(RegistrationCaseDO::getStatus, STATUS_COMPLETED, STATUS_CANCELLED);
        }
        if (orderIds != null) {
            if (orderIds.isEmpty()) query.eq(RegistrationCaseDO::getOrderId, -1L);
            else query.in(RegistrationCaseDO::getOrderId, orderIds);
        }
        if (caseIds != null) {
            if (caseIds.isEmpty()) query.eq(RegistrationCaseDO::getId, -1L);
            else query.in(RegistrationCaseDO::getId, caseIds);
        }
        return selectPage(pageParam, query
                .orderByDesc(RegistrationCaseDO::getUpdateTime)
                .orderByDesc(RegistrationCaseDO::getId));
    }
}
