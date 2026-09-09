package cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContentReviewRelationMapper {

    @Select("SELECT * FROM zsjos_user_relation WHERE scene=#{scene} AND target_user_id=#{operatorUserId} "
            + "AND status=0 AND tenant_id=#{tenantId} AND deleted=b'0' ORDER BY id")
    List<LeadAssignmentRelationDO> selectEnabledDirectorsForOperator(@Param("scene") String scene,
                                                                     @Param("operatorUserId") Long operatorUserId,
                                                                     @Param("tenantId") Long tenantId);
}
