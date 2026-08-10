package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentCursorDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface LeadAssignmentCursorMapper extends BaseMapperX<LeadAssignmentCursorDO> {
    @Insert("INSERT IGNORE INTO zsjos_lead_assignment_cursor " +
            "(rule_id, last_sales_user_id, version, creator, create_time, updater, update_time, deleted, tenant_id) " +
            "VALUES (#{ruleId}, NULL, 0, '0', NOW(), '0', NOW(), b'0', #{tenantId})")
    int ensureExists(Long ruleId, Long tenantId);

    @Select("SELECT * FROM zsjos_lead_assignment_cursor WHERE rule_id = #{ruleId} AND deleted = b'0' AND tenant_id = #{tenantId} FOR UPDATE")
    LeadAssignmentCursorDO selectByRuleIdForUpdate(Long ruleId, Long tenantId);
}
