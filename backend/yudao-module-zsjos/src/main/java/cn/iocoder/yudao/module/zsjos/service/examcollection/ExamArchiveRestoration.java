package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.*;

/** Replays field-level writes without mutating the original revision history. */
public final class ExamArchiveRestoration {
    private ExamArchiveRestoration() {}
    public record Revision(Snapshot snapshot, boolean excluded, Set<String> writtenKeys) {}
    public record Result(Snapshot snapshot, Set<String> restoredKeys, Set<String> retainedLaterKeys) {}

    public static Result restore(Snapshot baseline, List<Revision> history) {
        Snapshot previous = baseline;
        var resultFields = fields(baseline);
        var resultValues = values(baseline);
        var resultFiles = files(baseline);
        var excludedKeys = new LinkedHashSet<String>();
        var retained = new LinkedHashSet<String>();
        for (var revision : history) {
            Snapshot next = revision.snapshot();
            if (!Objects.equals(baseline.relationId(), next.relationId())
                    || !Objects.equals(baseline.personId(), next.personId()) || next.revision() != previous.revision() + 1)
                throw new IllegalArgumentException("Archive restoration requires a complete ordered service history");
            var oldFields = fields(previous); var nextFields = fields(next);
            var oldValues = values(previous); var nextValues = values(next);
            var oldFiles = files(previous); var nextFiles = files(next);
            var keys = new LinkedHashSet<String>(); keys.addAll(oldFields.keySet()); keys.addAll(nextFields.keySet());
            keys.addAll(oldValues.keySet()); keys.addAll(nextValues.keySet()); keys.addAll(oldFiles.keySet()); keys.addAll(nextFiles.keySet());
            keys.addAll(revision.writtenKeys());
            for (String key : keys) {
                boolean written = revision.writtenKeys().contains(key)
                        || !Objects.equals(JsonUtils.toJsonString(oldFields.get(key)), JsonUtils.toJsonString(nextFields.get(key)))
                        || !Objects.equals(value(oldValues.get(key)), value(nextValues.get(key)))
                        || !Objects.equals(oldFiles.get(key), nextFiles.get(key));
                if (!written) continue;
                if (revision.excluded()) { excludedKeys.add(key); continue; }
                replace(resultFields, nextFields, key); replace(resultValues, nextValues, key); replace(resultFiles, nextFiles, key);
                if (excludedKeys.contains(key)) retained.add(key);
            }
            // Diff against the actual prior snapshot, not the reconstructed one: otherwise an unrelated
            // subsequent save would accidentally reintroduce every excluded field.
            previous = next;
        }
        var restored = new LinkedHashSet<String>();
        for (String key : excludedKeys) if (!Objects.equals(value(values(previous).get(key)), value(resultValues.get(key)))
                || !Objects.equals(files(previous).get(key), resultFiles.get(key))
                || !Objects.equals(JsonUtils.toJsonString(fields(previous).get(key)), JsonUtils.toJsonString(resultFields.get(key)))) restored.add(key);
        return new Result(new Snapshot(baseline.relationId(), baseline.personId(), previous.revision(),
                List.copyOf(resultFields.values()), List.copyOf(resultValues.values()), resultFiles.values().stream().flatMap(List::stream).toList()),
                Set.copyOf(restored), Set.copyOf(retained));
    }
    private static <T> void replace(Map<String,T> target, Map<String,T> source, String key) {
        if (source.containsKey(key)) target.put(key, source.get(key)); else target.remove(key);
    }
    private static Map<String,Field> fields(Snapshot snapshot) {
        var result = new LinkedHashMap<String,Field>(); snapshot.fields().forEach(f -> result.put(f.getKey(),f)); return result;
    }
    private static Map<String,StudentInfoFormValueDO> values(Snapshot snapshot) {
        var result = new LinkedHashMap<String,StudentInfoFormValueDO>(); snapshot.answers().forEach(v -> result.put(v.getFieldKey(),v)); return result;
    }
    private static Map<String,List<Attachment>> files(Snapshot snapshot) {
        var result = new LinkedHashMap<String,List<Attachment>>();
        snapshot.attachments().forEach(f -> result.computeIfAbsent(f.fieldKey(), ignored -> new ArrayList<>()).add(f)); return result;
    }
    private static String value(StudentInfoFormValueDO value) {
        if (value == null) return null;
        // Ignore row IDs/audit timestamps; the business snapshot includes dictionary/area labels.
        return JsonUtils.toJsonString(Arrays.asList(value.getFieldType(), value.getSensitive(), value.getValueText(),
                value.getDictType(), value.getValueCode(), value.getValueLabelSnapshot(), value.getAreaCodePath(), value.getAreaLabelSnapshot(), value.getIdentityAreaSnapshot()));
    }
}
