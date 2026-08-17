package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationCaseMapper extends BaseMapperX<RegistrationCaseDO> {
    default RegistrationCaseDO selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<RegistrationCaseDO>().eq(RegistrationCaseDO::getOrderId, orderId));
    }

    @Select("SELECT * FROM zsjos_registration_case WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    RegistrationCaseDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<RegistrationCaseDO> selectPoolPage(PageParam pageParam, String status, java.util.List<Long> orderIds) {
        LambdaQueryWrapperX<RegistrationCaseDO> query = new LambdaQueryWrapperX<RegistrationCaseDO>()
                .eqIfPresent(RegistrationCaseDO::getStatus, status);
        if (orderIds != null) {
            if (orderIds.isEmpty()) query.eq(RegistrationCaseDO::getOrderId, -1L);
            else query.in(RegistrationCaseDO::getOrderId, orderIds);
        }
        return selectPage(pageParam, query
                .orderByAsc(RegistrationCaseDO::getRegistrationApprovedAt)
                .orderByAsc(RegistrationCaseDO::getId));
    }
}
