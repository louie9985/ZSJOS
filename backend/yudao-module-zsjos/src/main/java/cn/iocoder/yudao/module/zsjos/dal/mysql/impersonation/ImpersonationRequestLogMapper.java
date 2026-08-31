package cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation.ImpersonationRequestLogDO;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImpersonationRequestLogMapper extends BaseMapperX<ImpersonationRequestLogDO> {
    default PageResult<ImpersonationRequestLogDO> selectPage(PageParam page, Long sessionId) {
        return selectPage(page, new LambdaQueryWrapperX<ImpersonationRequestLogDO>()
                .eqIfPresent(ImpersonationRequestLogDO::getSessionId, sessionId)
                .orderByDesc(ImpersonationRequestLogDO::getOccurredAt));
    }
}
