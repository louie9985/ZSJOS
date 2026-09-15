package cn.iocoder.yudao.module.zsjos.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectSummaryRespVO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentSubjectSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.service.payment.PaymentSubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "ZSJOS - 支付主体配置")
@RestController
@RequestMapping("/zsjos/payment-subject")
@Validated
public class PaymentSubjectController {

    @Resource
    private PaymentSubjectService paymentSubjectService;

    @GetMapping("/page")
    @Operation(summary = "获得支付主体分页")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:query')")
    public CommonResult<PageResult<PaymentSubjectSummaryRespVO>> getPaymentSubjectPage(
            @Valid PaymentSubjectPageReqVO reqVO) {
        return success(BeanUtils.toBean(paymentSubjectService.getPaymentSubjectPage(reqVO), PaymentSubjectSummaryRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得支付主体选择项")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:payment-subject:query', 'zsjos:product-payment-subject:query', 'zsjos:product-payment-subject:configure')")
    public CommonResult<List<PaymentSubjectSummaryRespVO>> getPaymentSubjectSimpleList() {
        return success(BeanUtils.toBean(paymentSubjectService.getPaymentSubjectList(null), PaymentSubjectSummaryRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建支付主体配置")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:create')")
    public CommonResult<Long> createPaymentSubject(@Valid @RequestBody PaymentSubjectSaveReqVO createReqVO) {
        return success(paymentSubjectService.createPaymentSubject(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新支付主体配置")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:update')")
    public CommonResult<Boolean> updatePaymentSubject(@Valid @RequestBody PaymentSubjectSaveReqVO updateReqVO) {
        paymentSubjectService.updatePaymentSubject(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除支付主体配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:delete')")
    public CommonResult<Boolean> deletePaymentSubject(@RequestParam("id") Long id) {
        paymentSubjectService.deletePaymentSubject(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得支付主体配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:query')")
    public CommonResult<PaymentSubjectRespVO> getPaymentSubject(@RequestParam("id") Long id) {
        PaymentSubjectDO subject = paymentSubjectService.getPaymentSubject(id);
        return success(BeanUtils.toBean(subject, PaymentSubjectRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得支付主体配置列表")
    @Parameter(name = "status", description = "状态（0启用 1停用）", example = "0")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:query')")
    public CommonResult<List<PaymentSubjectRespVO>> getPaymentSubjectList(@RequestParam(value = "status", required = false) Integer status) {
        List<PaymentSubjectDO> list = paymentSubjectService.getPaymentSubjectList(status);
        return success(BeanUtils.toBean(list, PaymentSubjectRespVO.class));
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改支付主体状态")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-subject:update')")
    public CommonResult<Boolean> updatePaymentSubjectStatus(
            @RequestParam("id") Long id,
            @RequestParam("status") Integer status) {
        paymentSubjectService.updatePaymentSubjectStatus(id, status);
        return success(true);
    }
}
