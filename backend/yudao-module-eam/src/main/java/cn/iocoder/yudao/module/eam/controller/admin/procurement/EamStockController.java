package cn.iocoder.yudao.module.eam.controller.admin.procurement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockBalanceRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockMinimumReqVO;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/eam/stock")
public class EamStockController {
    @Resource private EamStockService stockService;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('eam:stock:query')")
    public CommonResult<List<EamStockBalanceRespVO>> list() {
        List<EamStockBalanceRespVO> result = stockService.getBalanceList().stream().map(item -> {
            EamStockBalanceRespVO vo = BeanUtils.toBean(item, EamStockBalanceRespVO.class);
            vo.setAvailableQuantity(item.getAvailableQuantity());
            return vo;
        }).toList();
        return success(result);
    }
    @PutMapping("/minimum")
    @PreAuthorize("@ss.hasPermission('eam:stock:update')")
    public CommonResult<Boolean> updateMinimum(@Valid @RequestBody EamStockMinimumReqVO reqVO) {
        stockService.updateMinimum(reqVO.getId(), reqVO.getMinimumQuantity());
        return success(true);
    }
}
