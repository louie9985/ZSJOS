package cn.iocoder.yudao.module.zsjos.controller.app.positioning.vo;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import lombok.Data;

@Data
public class PositioningConfirmationRespVO {
    private String state;
    private CardSnapshot card;

    public static PositioningConfirmationRespVO pending() {
        PositioningConfirmationRespVO response = new PositioningConfirmationRespVO();
        response.state = "pending_student_link";
        return response;
    }

    public static PositioningConfirmationRespVO ready(PositioningCardDO source) {
        PositioningConfirmationRespVO response = new PositioningConfirmationRespVO();
        response.state = "ready";
        response.card = new CardSnapshot(source.getId(), source.getCardNo(), source.getStatus(),
                source.getProfessionalRisk(), source.getLayer1Json(), source.getLayer2Json(), source.getFormulaJson(),
                source.getVersion());
        return response;
    }

    public record CardSnapshot(Long id, String cardNo, String status, Boolean professionalRisk,
                               String layer1Json, String layer2Json, String formulaJson, Integer version) {}
}
