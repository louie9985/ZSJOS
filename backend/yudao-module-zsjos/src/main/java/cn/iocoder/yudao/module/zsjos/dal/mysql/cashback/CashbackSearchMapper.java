package cn.iocoder.yudao.module.zsjos.dal.mysql.cashback;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CashbackSearchMapper {
    // Every relation in the server-compiled predicate carries tenant/deleted conditions.
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM zsjos_cashback c WHERE ${query.whereSql}")
    long count(@Param("query") AdvancedFilterQuery query);

    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @Select("SELECT c.* FROM zsjos_cashback c WHERE ${query.whereSql} "
            + "ORDER BY c.generated_at DESC, c.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<CashbackDO> page(@Param("query") AdvancedFilterQuery query, @Param("offset") long offset, @Param("limit") int limit);

    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @Select("SELECT c.id,c.lead_id,c.order_id,c.partner_id,c.beneficiary_user_id FROM zsjos_cashback c "
            + "WHERE ${query.whereSql} AND c.id > #{after} ORDER BY c.id LIMIT #{limit}")
    List<CashbackDO> candidates(@Param("query") AdvancedFilterQuery query, @Param("after") long after, @Param("limit") int limit);
}
