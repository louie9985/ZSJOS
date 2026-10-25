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
    /** A service owns one clock; draft intent, receipt additions and unchanged resubmissions do not start rounds. */
    public PositioningCardSubmissionDO diagnosisRound(MediaAccountDO account) {
        if (account.getCreateServiceRelationId() == null || account.getStudentPersonId() == null) return null;
        var completed = submissions.selectList(new LambdaQueryWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getServiceRelationId, account.getCreateServiceRelationId())
                .eq(PositioningCardSubmissionDO::getStudentPersonId, account.getStudentPersonId())
                .in(PositioningCardSubmissionDO::getStatus, List.of("confirmed", "superseded"))
                .orderByAsc(PositioningCardSubmissionDO::getSubmittedAt).orderByAsc(PositioningCardSubmissionDO::getId));
        PositioningCardSubmissionDO round = null;
        for (var submission : completed) {
            if (effectiveAt(submission) == null) continue;
            if (round == null || !sameContent(round, submission)) round = submission;
        }
        return round;
    }

    private static boolean sameContent(PositioningCardSubmissionDO left, PositioningCardSubmissionDO right) {
        // Compare frozen business content, excluding workflow actors, timestamps, receipts and template layout.
        return Objects.equals(parse(left.getValuesSnapshotJson()), parse(right.getValuesSnapshotJson()))
                && Objects.equals(parse(left.getDictSnapshotJson()), parse(right.getDictSnapshotJson()))
                && Objects.equals(contentJson(left.getLayer1Json()), contentJson(right.getLayer1Json()))
                && Objects.equals(contentJson(left.getLayer2Json()), contentJson(right.getLayer2Json()))
                && Objects.equals(contentJson(left.getFormulaJson()), contentJson(right.getFormulaJson()))
                && Objects.equals(contentJson(left.getFeasibilityJson()), contentJson(right.getFeasibilityJson()))
                && Objects.equals(contentJson(left.getContentFormJson()), contentJson(right.getContentFormJson()))
                && Objects.equals(contentJson(left.getComplianceJson()), contentJson(right.getComplianceJson()))
                && Objects.equals(left.getTrialEndDate(), right.getTrialEndDate())
                && Objects.equals(left.getProfessionalRisk(), right.getProfessionalRisk());
    }
    private static Object contentJson(String json) {
        return json == null || json.isBlank() ? Map.of() : JsonUtils.parseObject(json, Object.class);
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
