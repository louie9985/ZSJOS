package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.util.List;

@Data
public class PartnerLeadFilterOptionsRespVO {
    private List<Option> appealStatuses;
    private List<Option> orderReviewStatuses;

    public PartnerLeadFilterOptionsRespVO(List<Option> appealStatuses, List<Option> orderReviewStatuses) {
        this.appealStatuses = appealStatuses;
        this.orderReviewStatuses = orderReviewStatuses;
    }

    public record Option(String value, String label) {}
}
