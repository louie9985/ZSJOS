package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PointsApprovalPolicy {
    public static final String PROCESS_KEY = "zsjos_points_redemption";
    @Resource private BpmDefinitionReadApi definitions;

    public BpmProcessDefinitionMetadataRespDTO validate(Long applicant) {
        var definition = definitions.getPublishedProcessDefinition(PROCESS_KEY);
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended()))
            throw PointsErrors.processNotPublished();
        // BPM owns candidate resolution, self-approval handling and automatic approval settings.
        return definition;
    }
}
