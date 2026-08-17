package cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.WithdrawalDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WithdrawalMapper extends BaseMapperX<WithdrawalDO> {
    default PageResult<WithdrawalDO> selectPage(WithdrawalPageReqVO req, Long userId) {
        return selectPage(req, new LambdaQueryWrapperX<WithdrawalDO>()
                .eqIfPresent(WithdrawalDO::getApplicantUserId, userId)
                .eqIfPresent(WithdrawalDO::getStatus, req.getStatus())
                .orderByDesc(WithdrawalDO::getSubmittedAt).orderByDesc(WithdrawalDO::getId));
    }
    default PageResult<WithdrawalDO> selectPartnerPage(WithdrawalPageReqVO req, Long partnerId) {
        return selectPage(req, new LambdaQueryWrapperX<WithdrawalDO>()
                .eq(WithdrawalDO::getPartnerId, partnerId)
                .eqIfPresent(WithdrawalDO::getStatus, req.getStatus())
                .orderByDesc(WithdrawalDO::getSubmittedAt).orderByDesc(WithdrawalDO::getId));
    }
    @Select("SELECT * FROM zsjos_withdrawal WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    WithdrawalDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    default WithdrawalDO selectByProcessInstanceId(String processId) {
        return selectOne(WithdrawalDO::getProcessInstanceId, processId);
    }
    default WithdrawalDO selectByTransactionNo(String transactionNo) {
        return selectOne(WithdrawalDO::getBankTransactionNo, transactionNo);
    }
    default List<WithdrawalDO> selectPendingReminder(LocalDateTime overdueAt) {
        return selectList(new LambdaQueryWrapperX<WithdrawalDO>()
                .in(WithdrawalDO::getStatus, List.of("pending_review", "approved"))
                .le(WithdrawalDO::getSubmittedAt, overdueAt));
    }
}
