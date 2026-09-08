package cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.ClassTransferPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.ClassTransferRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClassTransferRequestMapper extends BaseMapperX<ClassTransferRequestDO> {
    default ClassTransferRequestDO selectPendingByRelation(Long relationId) {
        return selectOne(new LambdaQueryWrapperX<ClassTransferRequestDO>()
                .eq(ClassTransferRequestDO::getServiceRelationId, relationId)
                .eq(ClassTransferRequestDO::getStatus, "pending"));
    }

    default ClassTransferRequestDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(new LambdaQueryWrapperX<ClassTransferRequestDO>()
                .eq(ClassTransferRequestDO::getProcessInstanceId, processInstanceId));
    }

    @Select("SELECT * FROM zsjos_class_transfer_request WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND deleted=b'0' FOR UPDATE")
    ClassTransferRequestDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default PageResult<ClassTransferRequestDO> selectMyPage(ClassTransferPageReqVO req, Long userId) {
        return selectPage(req, new LambdaQueryWrapperX<ClassTransferRequestDO>()
                .eq(ClassTransferRequestDO::getApplicantUserId, userId)
                .eqIfPresent(ClassTransferRequestDO::getStatus, req.getStatus())
                .orderByDesc(ClassTransferRequestDO::getCreateTime).orderByDesc(ClassTransferRequestDO::getId));
    }
}
