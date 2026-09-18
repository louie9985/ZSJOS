package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonNoDailyCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface PersonNoDailyCounterMapper extends BaseMapperX<PersonNoDailyCounterDO> {

    /**
     * 预占当天序号。对当天已有行做递增，否则以 1 起算，超过 {@code maxSequence} 时回到 1。
     *
     * <p>这里刻意不使用 {@code LAST_INSERT_ID(expr)}：MySQL 在 INSERT 生成自增主键时，
     * 会用自增值覆盖 {@code LAST_INSERT_ID(expr)} 的结果，而本表的 AUTO_INCREMENT 受历史
     * 导入影响可能远大于每日上限，导致当天首个编号直接越界。
     */
    @Insert("""
            INSERT INTO zsjos_person_no_daily_counter
              (sequence_date, current_value, creator, create_time, updater, update_time, deleted, tenant_id)
            VALUES (#{sequenceDate}, 1, '', NOW(), '', NOW(), b'0', #{tenantId})
            ON DUPLICATE KEY UPDATE
              current_value = IF(current_value >= #{maxSequence}, 1, current_value + 1),
              update_time = NOW()
            """)
    int reserve(@Param("tenantId") Long tenantId, @Param("sequenceDate") LocalDate sequenceDate,
                @Param("maxSequence") long maxSequence);

    /** 读取本次预占的序号，并持有该行锁直至事务结束。 */
    @Select("""
            SELECT current_value
            FROM zsjos_person_no_daily_counter
            WHERE tenant_id=#{tenantId} AND sequence_date=#{sequenceDate}
            FOR UPDATE
            """)
    long selectReservedValue(@Param("tenantId") Long tenantId, @Param("sequenceDate") LocalDate sequenceDate);
}
