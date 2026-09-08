package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationClassAssignmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RegistrationClassAssignmentMapper extends BaseMapperX<RegistrationClassAssignmentDO> {
    default List<RegistrationClassAssignmentDO> selectByCaseId(Long caseId) {
        return selectList(new LambdaQueryWrapperX<RegistrationClassAssignmentDO>()
                .eq(RegistrationClassAssignmentDO::getRegistrationCaseId, caseId)
                .orderByAsc(RegistrationClassAssignmentDO::getOrderItemId));
    }

    default RegistrationClassAssignmentDO selectByCaseAndItem(Long caseId, Long orderItemId) {
        return selectOne(new LambdaQueryWrapperX<RegistrationClassAssignmentDO>()
                .eq(RegistrationClassAssignmentDO::getRegistrationCaseId, caseId)
                .eq(RegistrationClassAssignmentDO::getOrderItemId, orderItemId));
    }

    @Select("SELECT * FROM zsjos_registration_class_assignment WHERE registration_case_id=#{caseId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' ORDER BY order_item_id FOR UPDATE")
    List<RegistrationClassAssignmentDO> selectByCaseIdForUpdate(@Param("caseId") Long caseId,
                                                                 @Param("tenantId") Long tenantId);
}
