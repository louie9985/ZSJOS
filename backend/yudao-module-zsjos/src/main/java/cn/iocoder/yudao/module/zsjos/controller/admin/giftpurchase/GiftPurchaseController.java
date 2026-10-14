package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase;
import cn.iocoder.yudao.framework.common.pojo.*; import cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo.*; import cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase.GiftPurchaseDO; import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper; import cn.iocoder.yudao.framework.common.util.object.BeanUtils; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.annotation.Resource; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台-礼品采购") @RestController @RequestMapping("/zsjos/gift-purchase") public class GiftPurchaseController { @Resource private GiftPurchaseMapper mapper; @GetMapping("/get") @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:detail')") public CommonResult<GiftPurchaseRespVO> get(@RequestParam Long id){ return success(project(mapper.selectById(id))); } @GetMapping("/page") @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:query')") public CommonResult<PageResult<GiftPurchaseRespVO>> page(GiftPurchasePageReqVO req){ var p=mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<GiftPurchaseDO>(req.getPageNo(),req.getPageSize()), com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(GiftPurchaseDO.class).like(req.getStudentName()!=null,GiftPurchaseDO::getStudentName,req.getStudentName()).like(req.getOrderNo()!=null,GiftPurchaseDO::getOrderNo,req.getOrderNo()).like(req.getGiftKeyword()!=null,GiftPurchaseDO::getGiftItemsJson,req.getGiftKeyword()).orderByDesc(GiftPurchaseDO::getGeneratedAt)); return success(new PageResult<>(p.getRecords().stream().map(this::project).toList(),p.getTotal())); } private GiftPurchaseRespVO project(GiftPurchaseDO row) {
    if (row == null) return null;
    GiftPurchaseRespVO result = BeanUtils.toBean(row, GiftPurchaseRespVO.class);
    try { result.setGiftItemSnapshots(cn.iocoder.yudao.module.zsjos.service.order.SalesOrderGiftSnapshot.read(row.getGiftItemsJson())); }
    catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) { result.setGiftItemsInvalid(true); }
    return result;
} }



