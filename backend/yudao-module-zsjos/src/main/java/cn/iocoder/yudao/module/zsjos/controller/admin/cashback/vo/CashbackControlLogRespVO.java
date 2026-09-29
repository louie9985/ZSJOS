package cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class CashbackControlLogRespVO {
    private Long id;
    private String action;
    private String reason;
    private String operatorName;
    private LocalDateTime occurredAt;
}
