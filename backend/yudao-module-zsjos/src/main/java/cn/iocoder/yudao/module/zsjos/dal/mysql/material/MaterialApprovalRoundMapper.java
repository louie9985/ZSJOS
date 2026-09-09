package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialApprovalRoundDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialApprovalRoundMapper extends BaseMapperX<MaterialApprovalRoundDO> {
    default MaterialApprovalRoundDO selectByVersionAndRound(Long versionId, Integer roundNo) {
        return selectOne(new LambdaQueryWrapperX<MaterialApprovalRoundDO>()
                .eq(MaterialApprovalRoundDO::getMaterialVersionId, versionId)
                .eq(MaterialApprovalRoundDO::getRoundNo, roundNo));
    }

    default MaterialApprovalRoundDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(new LambdaQueryWrapperX<MaterialApprovalRoundDO>()
                .eq(MaterialApprovalRoundDO::getProcessInstanceId, processInstanceId));
    }

    @Select("SELECT * FROM zsjos_material_approval_round WHERE process_instance_id=#{processInstanceId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    MaterialApprovalRoundDO selectByProcessInstanceIdForUpdate(@Param("processInstanceId") String processInstanceId,
                                                               @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM zsjos_material_approval_round WHERE material_version_id=#{versionId} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' ORDER BY round_no DESC LIMIT 1 FOR UPDATE")
    MaterialApprovalRoundDO selectLatestForUpdate(@Param("versionId") Long versionId,
                                                  @Param("tenantId") Long tenantId);
}
