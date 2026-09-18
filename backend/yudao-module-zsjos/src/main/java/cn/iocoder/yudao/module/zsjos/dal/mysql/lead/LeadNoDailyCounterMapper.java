package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadNoDailyCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface LeadNoDailyCounterMapper extends BaseMapperX<LeadNoDailyCounterDO> {

    /**
     * 预占当天序号行（不存在则建出，初值 0），不参与取号本身。
     *
     * <p>这里刻意不使用 {@code LAST_INSERT_ID(expr)}：MySQL 在 INSERT 生成自增主键时，
     * 会用自增值覆盖 {@code LAST_INSERT_ID(expr)} 的结果，而本表的 AUTO_INCREMENT 受历史
     * 导入影响可能远大于每日上限，会导致当天首个编号直接越界并回滚，重试又回到同一分支。
     * 取号改为在 {@link #increment} 里完成，序号由 {@link #selectReservedValue} 从行上读回。
     */
    @Insert("""
            INSERT IGNORE INTO zsjos_lead_no_daily_counter
              (sequence_date, current_value, creator, create_time, updater, update_time, deleted, tenant_id)
            VALUES (#{sequenceDate}, 0, '', NOW(), '', NOW(), b'0', #{tenantId})
            """)
    int insertIfAbsent(@Param("tenantId") Long tenantId, @Param("sequenceDate") LocalDate sequenceDate);

    /** 原子自增；满 {@code maxSequence} 后回到 1 循环使用。行锁持有至事务结束。 */
    @Update("""
            UPDATE zsjos_lead_no_daily_counter
               SET current_value = IF(current_value >= #{maxSequence}, 1, current_value + 1),
                   update_time = NOW()
             WHERE tenant_id = #{tenantId} AND sequence_date = #{sequenceDate}
            """)
    int increment(@Param("tenantId") Long tenantId, @Param("sequenceDate") LocalDate sequenceDate,
                  @Param("maxSequence") long maxSequence);

    /** 读取本次预占的序号，并持有该行锁直至事务结束。 */
    @Select("""
            SELECT current_value
            FROM zsjos_lead_no_daily_counter
            WHERE tenant_id = #{tenantId} AND sequence_date = #{sequenceDate}
            FOR UPDATE
            """)
    long selectReservedValue(@Param("tenantId") Long tenantId, @Param("sequenceDate") LocalDate sequenceDate);
}
