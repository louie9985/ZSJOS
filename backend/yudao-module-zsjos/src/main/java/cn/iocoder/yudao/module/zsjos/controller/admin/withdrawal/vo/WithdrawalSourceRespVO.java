package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class WithdrawalSourceRespVO {
    private Long id;
    private BigDecimal amount;
    private Boolean active;
    private CashbackRespVO cashback;
}
