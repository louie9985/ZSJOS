package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectRespVO;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.ProductPaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.ProductPaymentSubjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PAYMENT_SUBJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PRODUCT_NOT_ENABLE;

/**
 * 产品支付主体关联 Service 实现类
 *
 * @author ZSJOS
 */
@Service
@Validated
@Slf4j
public class ProductPaymentSubjectServiceImpl implements ProductPaymentSubjectService {

    @Resource
    private ProductPaymentSubjectMapper productPaymentSubjectMapper;

    @Resource
    private PaymentSubjectService paymentSubjectService;

    @Resource
    private ZsjosProductMapper productMapper;

    @Override
    public PageResult<ProductPaymentSubjectRespVO> getProductPaymentSubjectPage(
            ProductPaymentSubjectPageReqVO reqVO) {
        var page = productPaymentSubjectMapper.selectProductPage(
                MyBatisUtils.buildPage(reqVO), reqVO,
                TenantContextHolder.getRequiredTenantId());
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void configureProductPaymentSubject(Long productId, Long paymentSubjectId) {
        validateEnabledProduct(productId);
        // 校验支付主体是否存在
        validatePaymentSubjectExists(paymentSubjectId);

        saveConfiguration(productId, paymentSubjectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchConfigureProductPaymentSubject(List<Long> productIds, Long paymentSubjectId) {
        // 校验支付主体是否存在
        validatePaymentSubjectExists(paymentSubjectId);

        // 固定加锁顺序，全部校验通过后再写入，避免混合有效/停用课程的批次部分保存。
        List<Long> distinctProductIds = productIds.stream().distinct().sorted().toList();
        distinctProductIds.forEach(this::validateEnabledProduct);
        distinctProductIds.forEach(id -> saveConfiguration(id, paymentSubjectId));
    }

    private void validateEnabledProduct(Long productId) {
        // 与课程停用共用行锁，保证状态校验到配置提交之间课程不会被停用。
        var product = productMapper.selectByIdForUpdate(productId, TenantContextHolder.getRequiredTenantId());
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus())) {
            throw exception(PRODUCT_NOT_ENABLE);
        }
    }

    private void saveConfiguration(Long productId, Long paymentSubjectId) {
        ProductPaymentSubjectDO relation = productPaymentSubjectMapper.selectByProductId(productId);
        if (relation != null) {
            // 唯一键含 deleted；每次软删再插会在第三次配置时与历史删除行冲突。
            relation.setPaymentSubjectId(paymentSubjectId);
            productPaymentSubjectMapper.updateById(relation);
        } else {
            productPaymentSubjectMapper.insert(ProductPaymentSubjectDO.builder()
                    .productId(productId).paymentSubjectId(paymentSubjectId).build());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProductPaymentSubject(Long productId) {
        productPaymentSubjectMapper.deleteByProductId(productId);
    }

    @Override
    public Long getPaymentSubjectIdByProductId(Long productId) {
        ProductPaymentSubjectDO relation = productPaymentSubjectMapper.selectByProductId(productId);
        return relation != null ? relation.getPaymentSubjectId() : null;
    }

    @Override
    public cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO getPaymentSubjectByProductId(Long productId) {
        Long paymentSubjectId = getPaymentSubjectIdByProductId(productId);
        if (paymentSubjectId == null) {
            return null;
        }
        return paymentSubjectService.getPaymentSubject(paymentSubjectId);
    }

    @Override
    public Map<Long, Long> getPaymentSubjectIdsByProductIds(List<Long> productIds) {
        List<ProductPaymentSubjectDO> relations = productPaymentSubjectMapper.selectByProductIds(productIds);
        return relations.stream()
                .collect(Collectors.toMap(
                        ProductPaymentSubjectDO::getProductId,
                        ProductPaymentSubjectDO::getPaymentSubjectId,
                        (v1, v2) -> v1
                ));
    }

    @Override
    public long countProductsByPaymentSubjectId(Long paymentSubjectId) {
        return productPaymentSubjectMapper.selectByPaymentSubjectId(paymentSubjectId).size();
    }

    private void validatePaymentSubjectExists(Long id) {
        if (paymentSubjectService.getPaymentSubject(id) == null) {
            throw exception(PAYMENT_SUBJECT_NOT_EXISTS);
        }
    }
}
