package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectPageReqVO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentSubjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Validated
public class PaymentSubjectServiceImpl implements PaymentSubjectService {

    @Resource
    private PaymentSubjectMapper paymentSubjectMapper;

    @Override
    public PageResult<PaymentSubjectDO> getPaymentSubjectPage(
            PaymentSubjectPageReqVO reqVO) {
        return paymentSubjectMapper.selectPage(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPaymentSubject(PaymentSubjectSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getSubjectCode());

        PaymentSubjectDO subject = BeanUtils.toBean(createReqVO, PaymentSubjectDO.class);
        paymentSubjectMapper.insert(subject);
        return subject.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePaymentSubject(PaymentSubjectSaveReqVO updateReqVO) {
        validateExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getSubjectCode());

        PaymentSubjectDO updateObj = BeanUtils.toBean(updateReqVO, PaymentSubjectDO.class);
        paymentSubjectMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePaymentSubject(Long id) {
        validateExists(id);
        paymentSubjectMapper.deleteById(id);
    }

    @Override
    public PaymentSubjectDO getPaymentSubject(Long id) {
        return paymentSubjectMapper.selectById(id);
    }

    @Override
    public PaymentSubjectDO getPaymentSubjectByCode(String subjectCode) {
        return paymentSubjectMapper.selectByCode(subjectCode);
    }

    @Override
    public List<PaymentSubjectDO> getPaymentSubjectList(Integer status) {
        return paymentSubjectMapper.selectListByStatus(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePaymentSubjectStatus(Long id, Integer status) {
        validateExists(id);
        PaymentSubjectDO updateObj = new PaymentSubjectDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        paymentSubjectMapper.updateById(updateObj);
    }

    @Override
    public PaymentSubjectDO getDefaultPaymentSubject() {
        return paymentSubjectMapper.selectDefault();
    }

    private void validateExists(Long id) {
        if (paymentSubjectMapper.selectById(id) == null) {
            throw exception(PAYMENT_SUBJECT_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        PaymentSubjectDO existing = paymentSubjectMapper.selectByCode(code);
        if (existing != null && !existing.getId().equals(id)) {
            throw exception(PAYMENT_SUBJECT_CODE_DUPLICATE);
        }
    }
}
