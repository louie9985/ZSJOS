package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.NewcomerPoolConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NewcomerPoolConfigMapper extends BaseMapperX<NewcomerPoolConfigDO> {
    @Insert("""
            INSERT INTO zsjos_newcomer_pool_config
              (user_id, enabled, daily_quota, creator, updater, tenant_id)
            VALUES (#{userId}, #{enabled}, #{dailyQuota}, #{operator}, #{operator}, #{tenantId})
            ON DUPLICATE KEY UPDATE enabled = #{enabled}, daily_quota = #{dailyQuota},
              updater = #{operator}, update_time = NOW()
            """)
    int save(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
             @Param("enabled") boolean enabled, @Param("dailyQuota") int dailyQuota,
             @Param("operator") String operator);

    default NewcomerPoolConfigDO selectByUserIdForUpdate(Long userId) {
        return selectOne(new LambdaQueryWrapperX<NewcomerPoolConfigDO>()
                .eq(NewcomerPoolConfigDO::getUserId, userId).last("FOR UPDATE"));
    }

    default NewcomerPoolConfigDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<NewcomerPoolConfigDO>()
                .eq(NewcomerPoolConfigDO::getUserId, userId));
    }
}
