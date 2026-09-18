package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderNumberCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkOrderNumberCounterMapper extends BaseMapperX<WorkOrderNumberCounterDO> {

    /**
     * 预占该 (租户, 前缀, 周期) 的流水行（不存在则建出，初值 0），不参与取号本身。
     *
     * <p>这里刻意不使用 {@code LAST_INSERT_ID(expr)}：MySQL 在 INSERT 生成自增主键时，
     * 会用自增值覆盖 {@code LAST_INSERT_ID(expr)} 的结果，而本表的 AUTO_INCREMENT 受历史
     * 导入影响可能远大于模板位数上限，会导致首个编号直接越界并回滚，重试又回到同一分支。
     */
    @Insert("""
            INSERT IGNORE INTO zsjos_work_order_number_counter
              (tenant_id,number_prefix,reset_key,current_value,creator,create_time,updater,update_time,deleted)
            VALUES (#{tenantId},#{prefix},#{resetKey},0,'system',NOW(),'system',NOW(),b'0')
            """)
    int insertIfAbsent(@Param("tenantId") Long tenantId, @Param("prefix") String prefix,
                       @Param("resetKey") String resetKey);

    /**
     * 原子自增，行锁持有至事务结束。
     *
     * <p>这里不回绕：复位周期为 NONE 时序号本就不该重复，越界交由调用方的位数校验抛出
     * {@code WORK_ORDER_NUMBER_OVERFLOW}；复位周期为日/月/年时由 reset_key 换行天然重置。
     */
    @Update("""
            UPDATE zsjos_work_order_number_counter
               SET current_value = current_value + 1, deleted = b'0', update_time = NOW()
             WHERE tenant_id = #{tenantId} AND number_prefix = #{prefix} AND reset_key = #{resetKey}
            """)
    int increment(@Param("tenantId") Long tenantId, @Param("prefix") String prefix,
                  @Param("resetKey") String resetKey);

    /** 读取本次预占的序号，并持有该行锁直至事务结束。 */
    @Select("""
            SELECT current_value
            FROM zsjos_work_order_number_counter
            WHERE tenant_id = #{tenantId} AND number_prefix = #{prefix} AND reset_key = #{resetKey}
            FOR UPDATE
            """)
    long selectAllocatedValue(@Param("tenantId") Long tenantId, @Param("prefix") String prefix,
                              @Param("resetKey") String resetKey);
}
