package cn.iocoder.yudao.module.zsjos.dal.mysql.audit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.audit.BusinessAuditLogDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditPageReqVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessAuditLogMapper extends BaseMapperX<BusinessAuditLogDO> {
    default PageResult<BusinessAuditLogDO> selectPage(BusinessAuditPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BusinessAuditLogDO>()
                .eqIfPresent(BusinessAuditLogDO::getCategoryCode, reqVO.getCategoryCode())
                .eqIfPresent(BusinessAuditLogDO::getActionCode, reqVO.getActionCode())
                .eqIfPresent(BusinessAuditLogDO::getTargetType, reqVO.getTargetType())
                .eqIfPresent(BusinessAuditLogDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(BusinessAuditLogDO::getResultStatus, reqVO.getResultStatus())
                .eqIfPresent(BusinessAuditLogDO::getOperatorUserId, reqVO.getOperatorUserId())
                .betweenIfPresent(BusinessAuditLogDO::getOccurredAt, reqVO.getOccurredAt())
                .orderByDesc(BusinessAuditLogDO::getOccurredAt));
    }
}
