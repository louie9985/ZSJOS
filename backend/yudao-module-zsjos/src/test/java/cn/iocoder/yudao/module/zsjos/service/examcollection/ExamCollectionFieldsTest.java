package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExamCollectionFieldsTest {
    @Test void emptySelectionGeneratesIdentityAndAttendanceFields() {
        var result = ExamCollectionFields.snapshot(new StudentInfoFields().presets(), List.of());
        assertEquals(List.of("name", "mobile", "id_card", "exam_attendance", "absence_reason"), result.stream().map(Field::getKey).toList());
        assertEquals(List.of("是", "否"), result.get(3).getOptions());
        assertTrue(result.stream().allMatch(f -> f.getRequired() && f.getEnabled()));
    }
    @Test void onlySelectedPublishedMaterialsAreIncludedAndRequired() {
        var published = new StudentInfoFields().presets();
        var result = ExamCollectionFields.snapshot(published, List.of("portrait", "certificate_shipping_address"));
        assertEquals(7, result.size());
        assertTrue(result.stream().allMatch(Field::getRequired));
        assertFalse(result.stream().anyMatch(f -> "identity_images".equals(f.getKey())));
        assertFalse(published.stream().filter(f -> "portrait".equals(f.getKey())).findFirst().orElseThrow().getRequired());
        result.getFirst().setLabel("changed snapshot");
        assertEquals("姓名", published.getFirst().getLabel());
    }
    @Test void rejectsGiftTextbookDisabledUnknownAndDuplicateSelections() {
        var published = new StudentInfoFields().presets();
        published.stream().filter(f -> "portrait".equals(f.getKey())).forEach(f -> f.setEnabled(false));
        for (var selection : List.of(List.of("gift_shipping_address"), List.of("textbook_shipping_address"),
                List.of("portrait"), List.of("unknown"), List.of("identity_images", "identity_images"))) {
            assertThrows(ServiceException.class, () -> ExamCollectionFields.snapshot(published, selection));
        }
    }
    @Test void uploadLimitsAreSnapshottedAndDefaultToTenMbOneFile() {
        var published = new StudentInfoFields().presets();
        var defaults = ExamCollectionFields.snapshot(published, List.of("portrait"), null).getLast();
        assertEquals(10, defaults.getMaxSizeMb());
        assertEquals(1, defaults.getMaxImages());
        var limit = new cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.UploadLimit();
        limit.setMaxSizeMb(20); limit.setMaxImages(3);
        var configured = ExamCollectionFields.snapshot(published, List.of("portrait"), java.util.Map.of("portrait", limit)).getLast();
        assertEquals(20, configured.getMaxSizeMb());
        assertEquals(3, configured.getMaxImages());
        assertThrows(ServiceException.class, () -> ExamCollectionFields.snapshot(published, List.of(), java.util.Map.of("portrait", limit)));
        limit.setMaxImages(0);
        assertThrows(ServiceException.class, () -> ExamCollectionFields.snapshot(published, List.of("portrait"), java.util.Map.of("portrait", limit)));
        var identity = ExamCollectionFields.snapshot(published, List.of("identity_images"), null).getLast();
        assertEquals(2, identity.getMaxImages());
        assertEquals(10, identity.getMaxSizeMb());
    }
    @Test void customFieldsPrecedeUploadsLikeThePublicForm() {
        var preset = ExamCollectionFields.snapshot(new StudentInfoFields().presets(), List.of("portrait", "certificate_shipping_address"), null);
        var field = new Field(); field.setKey("custom_exam"); field.setLabel("是否参考");
        field.setType("radio"); field.setRequired(false); field.setOptions(List.of("是", "否"));
        var result = ExamCollectionFields.customize(preset, List.of(field), null);
        assertEquals(List.of("name", "mobile", "id_card", "exam_attendance", "absence_reason", "certificate_shipping_address", "custom_exam", "portrait"),
                result.stream().map(Field::getKey).toList());
        assertEquals(List.of(10, 20, 30, 40, 50, 60, 70, 80), result.stream().map(Field::getSort).toList());
    }
    @Test void attendanceSelectsApplicableFields() {
        var fields = ExamCollectionFields.snapshot(new StudentInfoFields().presets(), List.of("portrait"), null);
        assertEquals(List.of("name", "mobile", "id_card", "exam_attendance", "portrait"),
                ExamCollectionFields.applicable(fields, "是").stream().map(Field::getKey).toList());
        assertEquals(List.of("name", "mobile", "id_card", "exam_attendance", "absence_reason"),
                ExamCollectionFields.applicable(fields, "否").stream().map(Field::getKey).toList());
        var legacy = fields.stream().filter(f -> !f.getKey().startsWith("exam_") && !f.getKey().equals("absence_reason")).toList();
        assertSame(legacy, ExamCollectionFields.applicable(legacy, "否"));
        var studentInfo = new StudentInfoFields();
        var declined = ExamCollectionFields.applicable(fields, "否");
        // A declined answer without its reason is rejected; the publicly documented synthetic checksum ID is used.
        assertThrows(ServiceException.class, () -> studentInfo.validateValues(declined,
                java.util.Map.of("name", "张三", "mobile", "13800000000", "id_card", "11010519491231002X", "exam_attendance", "否")));
        var answers = studentInfo.validateValues(declined, java.util.Map.of("name", "张三", "mobile", "13800000000", "id_card", "11010519491231002X", "exam_attendance", "否", "absence_reason", "工作冲突"));
        assertEquals("工作冲突", ExamCollectionFields.absenceReason(answers));
        assertNull(ExamCollectionFields.absenceReason(studentInfo.validateValues(ExamCollectionFields.applicable(fields, "是").stream().filter(f -> !"images".equals(f.getType())).toList(),
                java.util.Map.of("name", "张三", "mobile", "13800000000", "id_card", "11010519491231002X", "exam_attendance", "是"))));
    }
    @Test void customChoicesValidateAnswers() {
        var field = new Field(); field.setKey("custom_time"); field.setLabel("时间");
        field.setType("checkbox"); field.setRequired(true); field.setOptions(List.of("上午", "下午"));
        var result = ExamCollectionFields.customize(List.of(), List.of(field), java.util.Map.of("custom_time", "可多选"));
        assertEquals("可多选", result.getFirst().getNote());
        var values = new StudentInfoFields().validateCollectionValues(result, java.util.Map.of("custom_time", List.of("上午", "下午")));
        assertEquals("上午、下午", values.getFirst().getValueLabelSnapshot());
        assertThrows(ServiceException.class, () -> new StudentInfoFields().validateCollectionValues(result, java.util.Map.of("custom_time", List.of("晚上"))));
        field.setOptions(List.of("重复", "重复"));
        assertThrows(ServiceException.class, () -> ExamCollectionFields.customize(List.of(), List.of(field), null));
    }
}
