package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderCommandDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesOrderCommandMapper extends BaseMapperX<SalesOrderCommandDO> {
    @Insert("INSERT IGNORE INTO zsjos_order_command(idempotency_key,order_id,approval_round_id,process_instance_id,"
            + "command_type,task_definition_key,bpm_task_id,operator_user_id,request_fingerprint,creator,create_time,"
            + "updater,update_time,deleted,tenant_id) VALUES(#{row.idempotencyKey},#{row.orderId},"
            + "#{row.approvalRoundId},#{row.processInstanceId},#{row.commandType},#{row.taskDefinitionKey},"
            + "#{row.bpmTaskId},#{row.operatorUserId},#{row.requestFingerprint},'',NOW(),'',NOW(),b'0',#{tenantId})")
    int insertIgnore(@Param("tenantId") Long tenantId, @Param("row") SalesOrderCommandDO row);

    default SalesOrderCommandDO selectByIdempotencyKey(String key) {
        return selectOne(SalesOrderCommandDO::getIdempotencyKey, key);
    }
}
