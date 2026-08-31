package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderNumberCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkOrderNumberCounterMapper extends BaseMapperX<WorkOrderNumberCounterDO> {
    @Insert("""
            INSERT INTO zsjos_work_order_number_counter
              (tenant_id,number_prefix,reset_key,current_value,creator,create_time,updater,update_time,deleted)
            VALUES (#{tenantId},#{prefix},#{resetKey},LAST_INSERT_ID(1),'system',NOW(),'system',NOW(),b'0')
            ON DUPLICATE KEY UPDATE current_value=LAST_INSERT_ID(current_value+1),deleted=b'0',update_time=NOW()
            """)
    int increment(@Param("tenantId") Long tenantId, @Param("prefix") String prefix, @Param("resetKey") String resetKey);

    @Select("SELECT LAST_INSERT_ID()")
    long selectAllocatedValue();
}
