package cn.iocoder.yudao.module.zsjos.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.ProductPaymentSubjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.metadata.IPage;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectRespVO;

import java.util.List;

/**
 * 产品支付主体关联 Mapper
 *
 * @author ZSJOS
 */
@Mapper
public interface ProductPaymentSubjectMapper extends BaseMapperX<ProductPaymentSubjectDO> {

    // 从产品出发保留未配置行；关联表也显式约束租户和逻辑删除，避免读取历史关联。
    @Select("""
            <script>
            SELECT p.id AS product_id, p.name AS product_name,
                   r.payment_subject_id, s.subject_code, s.subject_name, r.update_time AS config_time
            FROM zsjos_product p
            LEFT JOIN zsjos_product_payment_subject r ON r.product_id = p.id
                AND r.tenant_id = p.tenant_id AND r.deleted = 0
            LEFT JOIN zsjos_payment_subject s ON s.id = r.payment_subject_id
                AND s.tenant_id = p.tenant_id AND s.deleted = 0
            WHERE p.deleted = 0 AND p.tenant_id = #{tenantId}
            <if test="req.productName != null and req.productName != ''">
                AND p.name LIKE CONCAT('%', #{req.productName}, '%')
            </if>
            <if test="req.paymentSubjectId != null">
                AND r.payment_subject_id = #{req.paymentSubjectId}
            </if>
            ORDER BY p.sort ASC, p.id DESC
            </script>
            """)
    IPage<ProductPaymentSubjectRespVO> selectProductPage(IPage<ProductPaymentSubjectRespVO> page,
            @Param("req") ProductPaymentSubjectPageReqVO reqVO, @Param("tenantId") Long tenantId);

    default ProductPaymentSubjectDO selectByProductId(Long productId) {
        return selectOne(ProductPaymentSubjectDO::getProductId, productId);
    }

    default List<ProductPaymentSubjectDO> selectByProductIds(List<Long> productIds) {
        return selectList(ProductPaymentSubjectDO::getProductId, productIds);
    }

    default List<ProductPaymentSubjectDO> selectByPaymentSubjectId(Long paymentSubjectId) {
        return selectList(ProductPaymentSubjectDO::getPaymentSubjectId, paymentSubjectId);
    }

    default int deleteByProductId(Long productId) {
        return delete(ProductPaymentSubjectDO::getProductId, productId);
    }
}
