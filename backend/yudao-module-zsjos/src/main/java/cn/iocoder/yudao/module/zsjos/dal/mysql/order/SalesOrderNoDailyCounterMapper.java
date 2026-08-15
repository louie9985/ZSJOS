package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderNoDailyCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface SalesOrderNoDailyCounterMapper extends BaseMapperX<SalesOrderNoDailyCounterDO> {
    @Insert("""
            INSERT INTO zsjos_order_no_daily_counter
              (sequence_date, current_value, creator, create_time, updater, update_time, deleted, tenant_id)
            VALUES (#{sequenceDate}, LAST_INSERT_ID(1), '', NOW(), '', NOW(), b'0', #{tenantId})
            ON DUPLICATE KEY UPDATE
              current_value = LAST_INSERT_ID(IF(current_value >= 9999, 1, current_value + 1)), update_time = NOW()
            """)
    int reserve(@Param("tenantId") Long tenantId, @Param("sequenceDate") LocalDate sequenceDate);

    @Select("SELECT LAST_INSERT_ID()")
    long selectReservedValue();
}
