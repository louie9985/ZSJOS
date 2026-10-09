package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.NewcomerPoolDailyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface NewcomerPoolDailyMapper extends BaseMapperX<NewcomerPoolDailyDO> {
    @Insert("""
            INSERT INTO zsjos_newcomer_pool_daily
              (user_id, business_date, accepted_count, creator, create_time, updater, update_time, deleted, tenant_id)
            VALUES (#{userId}, #{date}, 1, '', NOW(), '', NOW(), b'0', #{tenantId})
            ON DUPLICATE KEY UPDATE accepted_count = accepted_count + 1, update_time = NOW()
            """)
    int increment(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                  @Param("date") LocalDate date);

    default NewcomerPoolDailyDO selectByUserAndDateForUpdate(Long userId, LocalDate date) {
        return selectOne(new LambdaQueryWrapperX<NewcomerPoolDailyDO>()
                .eq(NewcomerPoolDailyDO::getUserId, userId)
                .eq(NewcomerPoolDailyDO::getBusinessDate, date).last("FOR UPDATE"));
    }

    default NewcomerPoolDailyDO selectByUserAndDate(Long userId, LocalDate date) {
        return selectOne(new LambdaQueryWrapperX<NewcomerPoolDailyDO>()
                .eq(NewcomerPoolDailyDO::getUserId, userId)
                .eq(NewcomerPoolDailyDO::getBusinessDate, date));
    }
}
