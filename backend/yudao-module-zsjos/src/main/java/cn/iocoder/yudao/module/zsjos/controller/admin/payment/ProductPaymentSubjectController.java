package cn.iocoder.yudao.module.zsjos.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectBatchConfigReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectConfigReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.service.payment.PaymentSubjectService;
import cn.iocoder.yudao.module.zsjos.service.payment.ProductPaymentSubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "ZSJOS - 产品支付主体配置")
@RestController
@RequestMapping("/zsjos/product-payment-subject")
@Validated
public class ProductPaymentSubjectController {

    @Resource
    private ProductPaymentSubjectService productPaymentSubjectService;

    @Resource
    private PaymentSubjectService paymentSubjectService;

    @GetMapping("/page")
    @Operation(summary = "获得产品支付主体配置分页（包含未配置产品）")
    @PreAuthorize("@ss.hasPermission('zsjos:product-payment-subject:query')")
    public CommonResult<PageResult<ProductPaymentSubjectRespVO>> getProductPaymentSubjectPage(
            @Valid ProductPaymentSubjectPageReqVO reqVO) {
        return success(productPaymentSubjectService.getProductPaymentSubjectPage(reqVO));
    }

    @PostMapping("/configure")
    @Operation(summary = "配置产品支付主体")
    @PreAuthorize("@ss.hasPermission('zsjos:product-payment-subject:configure')")
    public CommonResult<Boolean> configureProductPaymentSubject(@Valid @RequestBody ProductPaymentSubjectConfigReqVO reqVO) {
        productPaymentSubjectService.configureProductPaymentSubject(reqVO.getProductId(), reqVO.getPaymentSubjectId());
        return success(true);
    }

    @PostMapping("/batch-configure")
    @Operation(summary = "批量配置产品支付主体")
    @PreAuthorize("@ss.hasPermission('zsjos:product-payment-subject:configure')")
    public CommonResult<Boolean> batchConfigureProductPaymentSubject(@Valid @RequestBody ProductPaymentSubjectBatchConfigReqVO reqVO) {
        productPaymentSubjectService.batchConfigureProductPaymentSubject(reqVO.getProductIds(), reqVO.getPaymentSubjectId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除产品支付主体配置")
    @Parameter(name = "productId", description = "产品编号", required = true)
    @PreAuthorize("@ss.hasPermission('zsjos:product-payment-subject:configure')")
    public CommonResult<Boolean> deleteProductPaymentSubject(@RequestParam("productId") Long productId) {
        productPaymentSubjectService.deleteProductPaymentSubject(productId);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取产品的支付主体配置")
    @Parameter(name = "productId", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zsjos:product-payment-subject:query')")
    public CommonResult<ProductPaymentSubjectRespVO> getProductPaymentSubject(@RequestParam("productId") Long productId) {
        Long paymentSubjectId = productPaymentSubjectService.getPaymentSubjectIdByProductId(productId);
        if (paymentSubjectId == null) {
            return success(null);
        }

        PaymentSubjectDO subject = paymentSubjectService.getPaymentSubject(paymentSubjectId);
        if (subject == null) {
            return success(null);
        }

        ProductPaymentSubjectRespVO respVO = new ProductPaymentSubjectRespVO();
        respVO.setProductId(productId);
        respVO.setPaymentSubjectId(paymentSubjectId);
        respVO.setSubjectCode(subject.getSubjectCode());
        respVO.setSubjectName(subject.getSubjectName());
        return success(respVO);
    }

    @GetMapping("/batch-get")
    @Operation(summary = "批量获取产品的支付主体配置")
    @Parameter(name = "productIds", description = "产品编号列表", required = true, example = "1,2,3")
    @PreAuthorize("@ss.hasPermission('zsjos:product-payment-subject:query')")
    public CommonResult<List<ProductPaymentSubjectRespVO>> batchGetProductPaymentSubject(@RequestParam("productIds") List<Long> productIds) {
        Map<Long, Long> productSubjectMap = productPaymentSubjectService.getPaymentSubjectIdsByProductIds(productIds);

        // 获取所有支付主体信息
        List<Long> subjectIds = productSubjectMap.values().stream().distinct().collect(Collectors.toList());
        List<PaymentSubjectDO> subjects = paymentSubjectService.getPaymentSubjectList(null);
        Map<Long, PaymentSubjectDO> subjectMap = subjects.stream()
                .collect(Collectors.toMap(PaymentSubjectDO::getId, s -> s));

        // 组装响应
        List<ProductPaymentSubjectRespVO> respList = productSubjectMap.entrySet().stream()
                .map(entry -> {
                    ProductPaymentSubjectRespVO respVO = new ProductPaymentSubjectRespVO();
                    respVO.setProductId(entry.getKey());
                    respVO.setPaymentSubjectId(entry.getValue());

                    PaymentSubjectDO subject = subjectMap.get(entry.getValue());
                    if (subject != null) {
                        respVO.setSubjectCode(subject.getSubjectCode());
                        respVO.setSubjectName(subject.getSubjectName());
                    }
                    return respVO;
                })
                .collect(Collectors.toList());

        return success(respList);
    }
}
