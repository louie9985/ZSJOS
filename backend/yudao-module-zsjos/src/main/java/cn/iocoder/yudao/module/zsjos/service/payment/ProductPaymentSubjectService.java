package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectRespVO;
import java.util.List;
import java.util.Map;

/**
 * 产品支付主体关联 Service 接口
 *
 * @author ZSJOS
 */
public interface ProductPaymentSubjectService {

    PageResult<ProductPaymentSubjectRespVO> getProductPaymentSubjectPage(
            ProductPaymentSubjectPageReqVO reqVO);

    /**
     * 配置产品的支付主体
     *
     * @param productId 产品编号
     * @param paymentSubjectId 支付主体编号
     */
    void configureProductPaymentSubject(Long productId, Long paymentSubjectId);

    /**
     * 批量配置产品的支付主体
     *
     * @param productIds 产品编号列表
     * @param paymentSubjectId 支付主体编号
     */
    void batchConfigureProductPaymentSubject(List<Long> productIds, Long paymentSubjectId);

    /**
     * 删除产品的支付主体配置
     *
     * @param productId 产品编号
     */
    void deleteProductPaymentSubject(Long productId);

    /**
     * 获取产品的支付主体编号
     *
     * @param productId 产品编号
     * @return 支付主体编号，如果未配置则返回 null
     */
    Long getPaymentSubjectIdByProductId(Long productId);

    /**
     * 获取产品的支付主体对象
     *
     * @param productId 产品编号
     * @return 支付主体对象，如果未配置则返回 null
     */
    cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO getPaymentSubjectByProductId(Long productId);

    /**
     * 批量获取产品的支付主体编号
     *
     * @param productIds 产品编号列表
     * @return 产品编号 -> 支付主体编号的映射
     */
    Map<Long, Long> getPaymentSubjectIdsByProductIds(List<Long> productIds);

    /**
     * 获取使用指定支付主体的产品数量
     *
     * @param paymentSubjectId 支付主体编号
     * @return 产品数量
     */
    long countProductsByPaymentSubjectId(Long paymentSubjectId);
}
