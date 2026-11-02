package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudentInfoReusePolicyTest {
    private Field field(String key, String type, boolean required) {
        Field field = new Field(); field.setKey(key); field.setLabel(key); field.setType(type);
        field.setEnabled(true); field.setRequired(required); field.setMaxImages(9); field.setMaxSizeMb(100);
        field.setAllowedExtensions(List.of("jpg", "png")); return field;
    }
    private StudentInfoFormValueDO answer(String key, String text) {
        var answer = new StudentInfoFormValueDO(); answer.setFieldKey(key); answer.setValueText(text); return answer;
    }
    @Test void educationRequiredFieldsPreventDirectReuseButKeepCompatiblePrefill() {
        var name = field("name", "text", true); var school = field("target_school", "text", true);
        var answers = List.of(answer("name", "示例"));
        assertEquals(List.of("target_school"), StudentInfoReusePolicy.missing(List.of(name, school), List.of(name), answers, List.of()));
        assertEquals(java.util.Map.of("name", "示例"), StudentInfoReusePolicy.prefill(List.of(name, school), List.of(name), answers).getValues());
    }
    @Test void optionalBlankDoesNotBlockAndBlankRequiredDoes() {
        var name = field("name", "text", true); var school = field("school", "text", false);
        assertTrue(StudentInfoReusePolicy.missing(List.of(name, school), List.of(name), List.of(answer("name", "示例")), List.of()).isEmpty());
        assertEquals(List.of("name"), StudentInfoReusePolicy.missing(List.of(name), List.of(name), List.of(answer("name", " ")), List.of()));
    }
    @Test void differentDictionaryCannotReuseAndSnapshotLabelIsPreserved() {
        var target = field("education_level", "dict", true); target.setDictType("current");
        var source = field("education_level", "dict", true); source.setDictType("previous");
        var answer = answer("education_level", null); answer.setValueCode("1"); answer.setDictType("current"); answer.setValueLabelSnapshot("原始标签");
        assertFalse(StudentInfoReusePolicy.missing(List.of(target), List.of(source), List.of(answer), List.of()).isEmpty());
        assertTrue(StudentInfoReusePolicy.prefill(List.of(target), List.of(source), List.of(answer)).getValues().isEmpty());
        assertEquals("原始标签", StudentInfoReusePolicy.prefill(List.of(target), List.of(target), List.of(answer)).getSelections().get("education_level").getLabel());
    }
    @Test void identityRequiresBothSidesAndCompatibleAttachmentFormat() {
        var field = field("identity_images", "images", true);
        var front = new Image(); front.setFieldKey("identity_images"); front.setSide("front"); front.setContentType("image/jpeg"); front.setSize(10L);
        var back = new Image(); back.setFieldKey("identity_images"); back.setSide("back"); back.setContentType("image/jpeg"); back.setSize(10L);
        assertFalse(StudentInfoReusePolicy.missing(List.of(field), List.of(field), List.of(), List.of(front)).isEmpty());
        assertTrue(StudentInfoReusePolicy.missing(List.of(field), List.of(field), List.of(), List.of(front, back)).isEmpty());
        back.setContentType("application/pdf");
        assertFalse(StudentInfoReusePolicy.missing(List.of(field), List.of(field), List.of(), List.of(front, back)).isEmpty());
    }
}
