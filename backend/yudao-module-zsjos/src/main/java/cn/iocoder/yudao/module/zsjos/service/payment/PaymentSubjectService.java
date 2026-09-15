package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 支付主体配置 Service 接口
 */
public interface PaymentSubjectService {

    PageResult<PaymentSubjectDO> getPaymentSubjectPage(
            PaymentSubjectPageReqVO reqVO);

    /**
     * 创建支付主体
     */
    Long createPaymentSubject(@Valid PaymentSubjectSaveReqVO createReqVO);

    /**
     * 更新支付主体
     */
    void updatePaymentSubject(@Valid PaymentSubjectSaveReqVO updateReqVO);

    /**
     * 删除支付主体
     */
    void deletePaymentSubject(Long id);

    /**
     * 获得支付主体
     */
    PaymentSubjectDO getPaymentSubject(Long id);

    /**
     * 根据编码获取支付主体
     */
    PaymentSubjectDO getPaymentSubjectByCode(String subjectCode);

    /**
     * 获得支付主体列表
     */
    List<PaymentSubjectDO> getPaymentSubjectList(Integer status);

    /**
     * 更新支付主体状态
     */
    void updatePaymentSubjectStatus(Long id, Integer status);

    /**
     * 获取默认支付主体
     */
    PaymentSubjectDO getDefaultPaymentSubject();
}
