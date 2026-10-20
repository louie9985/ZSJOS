package cn.iocoder.yudao.module.zsjos.dal.mysql.navigation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.navigation.NavigationPreferenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NavigationPreferenceMapper extends BaseMapperX<NavigationPreferenceDO> {
    default NavigationPreferenceDO selectPreference(Long userId, String client) {
        return selectOne(NavigationPreferenceDO::getUserId, userId, NavigationPreferenceDO::getClient, client);
    }

    // The unique tenant/user/client key also makes simultaneous first saves safe.
    @Insert("""
        INSERT INTO zsjos_navigation_preference
          (tenant_id,user_id,client,preference_json,creator,updater)
        VALUES (#{tenantId},#{userId},#{client},#{json},#{userId},#{userId})
        ON DUPLICATE KEY UPDATE preference_json=#{json},updater=#{userId},update_time=CURRENT_TIMESTAMP
        """)
    void savePreference(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                        @Param("client") String client, @Param("json") String json);
}
