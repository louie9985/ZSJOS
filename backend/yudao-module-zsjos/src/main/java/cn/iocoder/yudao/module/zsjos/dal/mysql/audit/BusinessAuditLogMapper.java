package cn.iocoder.yudao.module.zsjos.dal.mysql.audit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.audit.BusinessAuditLogDO;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessAuditLogMapper extends BaseMapperX<BusinessAuditLogDO> {
    default PageResult<BusinessAuditLogDO> selectPage(PageParam page, String actionCode, String targetType) {
        return selectPage(page, new LambdaQueryWrapperX<BusinessAuditLogDO>()
                .eqIfPresent(BusinessAuditLogDO::getActionCode, actionCode)
                .eqIfPresent(BusinessAuditLogDO::getTargetType, targetType)
                .orderByDesc(BusinessAuditLogDO::getOccurredAt));
    }
}
