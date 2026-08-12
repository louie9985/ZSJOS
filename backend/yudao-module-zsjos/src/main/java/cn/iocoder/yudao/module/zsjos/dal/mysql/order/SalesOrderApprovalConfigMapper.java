package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesOrderApprovalConfigMapper extends BaseMapperX<SalesOrderApprovalConfigDO> {
    default SalesOrderApprovalConfigDO selectCurrent() {
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<SalesOrderApprovalConfigDO>()
                .orderByDesc(SalesOrderApprovalConfigDO::getId).last("LIMIT 1"));
    }
}
