package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningEvidenceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Delivery uses the latest effective service-scoped card, independently of manual account application. */
@Service
public class DeliveryPositioningSource {
    @Resource private PositioningCardSubmissionMapper submissions;
    public PositioningCardSubmissionDO latest(MediaAccountDO account) {
        if (account.getCreateServiceRelationId() == null || account.getStudentPersonId() == null) return null;
        return submissions.selectList(new LambdaQueryWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getServiceRelationId, account.getCreateServiceRelationId())
                .eq(PositioningCardSubmissionDO::getStudentPersonId, account.getStudentPersonId())
                .in(PositioningCardSubmissionDO::getStatus, List.of("confirmed", "superseded", "student_agreed"))
                .orderByDesc(PositioningCardSubmissionDO::getSubmittedAt).orderByDesc(PositioningCardSubmissionDO::getId))
                .stream().filter(PositioningEvidenceService::eligible).findFirst().orElse(null);
    }
    /** Diagnosis requires actual evidence even for grandfathered application versions. */
    public PositioningCardSubmissionDO firstProven(MediaAccountDO account) {
        if (account.getCreateServiceRelationId() == null || account.getStudentPersonId() == null) return null;
        return submissions.selectList(new LambdaQueryWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getServiceRelationId, account.getCreateServiceRelationId())
                .eq(PositioningCardSubmissionDO::getStudentPersonId, account.getStudentPersonId())
                .in(PositioningCardSubmissionDO::getStatus, List.of("confirmed", "superseded")))
                .stream().filter(s -> effectiveAt(s) != null)
                .min(Comparator.comparing(DeliveryPositioningSource::effectiveAt)).orElse(null);
    }
    public static java.time.LocalDateTime effectiveAt(PositioningCardSubmissionDO source) {
        if (source == null || source.getStudentDecidedAt() == null || !List.of("confirmed", "superseded").contains(source.getStatus())) return null;
        var receipt = PositioningEvidenceService.evidence(source).stream().map(e -> e.uploadedAt())
                .filter(Objects::nonNull).min(java.time.LocalDateTime::compareTo).orElse(null);
        return receipt == null ? null : (receipt.isBefore(source.getStudentDecidedAt()) ? source.getStudentDecidedAt() : receipt);
    }
    public Map<String,Object> snapshot(PositioningCardSubmissionDO source, String stage) {
        if (source == null) return Map.of();
        String key = "pc_delivery_" + stage.toLowerCase(Locale.ROOT);
        Map<String,Object> values = parse(source.getValuesSnapshotJson()), dict = parse(source.getDictSnapshotJson());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("cardId", source.getCardId()); result.put("submissionId", source.getId());
        result.put("submissionNo", source.getSubmissionNo()); result.put("agreement", values.get(key));
        result.put("references", dict.getOrDefault(key + "_refs", List.of()));
        return result;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> parse(String json) { return json == null || json.isBlank() ? new LinkedHashMap<>() : JsonUtils.parseObject(json, Map.class); }
}
