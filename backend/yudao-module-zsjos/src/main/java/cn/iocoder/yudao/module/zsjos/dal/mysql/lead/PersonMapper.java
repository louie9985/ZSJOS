package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Mapper
public interface PersonMapper extends BaseMapperX<PersonDO> {
    default PersonDO selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapperX<PersonDO>().eqIfPresent(PersonDO::getMobile, mobile));
    }
    default PersonDO selectByWechatId(String wechatId) {
        return selectOne(new LambdaQueryWrapperX<PersonDO>().eqIfPresent(PersonDO::getWechatId, wechatId));
    }

    default List<PersonDO> selectDuplicateCandidates(String mobile, String wechatId) {
        if (mobile == null && wechatId == null) return List.of();
        Set<Long> ids = new LinkedHashSet<>();
        LambdaQueryWrapperX<PersonDO> query = new LambdaQueryWrapperX<>();
        query.and(q -> {
            boolean hasPrevious = false;
            if (mobile != null) {
                q.eq(PersonDO::getMobile, mobile).or().eq(PersonDO::getWechatId, mobile);
                hasPrevious = true;
            }
            if (wechatId != null) {
                if (hasPrevious) q.or();
                q.eq(PersonDO::getWechatId, wechatId).or().eq(PersonDO::getMobile, wechatId);
            }
        });
        List<PersonDO> rows = selectList(query);
        return rows.stream().filter(row -> ids.add(row.getId())).toList();
    }
}
