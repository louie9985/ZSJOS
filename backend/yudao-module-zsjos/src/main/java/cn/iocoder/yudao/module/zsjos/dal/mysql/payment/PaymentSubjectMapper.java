package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaymentSubjectMapper extends BaseMapperX<PaymentSubjectDO> {
    default PageResult<PaymentSubjectDO> selectPage(
            PaymentSubjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PaymentSubjectDO>()
                .likeIfPresent(PaymentSubjectDO::getSubjectName, reqVO.getSubjectName())
                .eqIfPresent(PaymentSubjectDO::getStatus, reqVO.getStatus())
                .orderByAsc(PaymentSubjectDO::getSort)
                .orderByDesc(PaymentSubjectDO::getId));
    }

    default PaymentSubjectDO selectByCode(String subjectCode) {
        return selectOne(new LambdaQueryWrapperX<PaymentSubjectDO>()
                .eq(PaymentSubjectDO::getSubjectCode, subjectCode)
                .last("LIMIT 1"));
    }

    default List<PaymentSubjectDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<PaymentSubjectDO>()
                .eqIfPresent(PaymentSubjectDO::getStatus, status)
                .orderByAsc(PaymentSubjectDO::getSort)
                .orderByDesc(PaymentSubjectDO::getId));
    }

    default PaymentSubjectDO selectDefault() {
        return selectOne(new LambdaQueryWrapperX<PaymentSubjectDO>()
                .eq(PaymentSubjectDO::getIsDefault, true)
                .eq(PaymentSubjectDO::getStatus, 0)
                .last("LIMIT 1"));
    }
}
