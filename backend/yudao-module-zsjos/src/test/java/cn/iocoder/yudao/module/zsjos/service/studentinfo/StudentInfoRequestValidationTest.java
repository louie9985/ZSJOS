package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentInfoRequestValidationTest {
    @Test void configurationWithTextbookAddressPassesRequestValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var fields = new ArrayList<>(new StudentInfoFields().presets());
            var request = new StudentInfoVO.Save(); request.setRevision(0); request.setFields(fields);
            assertEquals(33, fields.size());
            assertTrue(fields.stream().anyMatch(f -> "textbook_shipping_address".equals(f.getKey())));
            assertTrue(validator.validate(request).isEmpty());
            fields.add(fields.getFirst());
            assertTrue(validator.validate(request).stream().anyMatch(v -> "fields".equals(v.getPropertyPath().toString())));
        }
    }

    @Test void registrationAllows33FieldsAndServiceArchiveAllowsAdditionalWechatField() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var values = new LinkedHashMap<String, Object>();
            var draftValues = new LinkedHashMap<String, String>();
            for (var field : new StudentInfoFields().presets()) {
                values.put(field.getKey(), ""); draftValues.put(field.getKey(), "");
            }
            var submit = new StudentInfoVO.Submit(); submit.setRevision(0L); submit.setValues(values);
            var draft = new StudentInfoVO.DraftWrite(); draft.setRevision(0L); draft.setValues(draftValues);
            var archive = new StudentArchiveVO.Save(); archive.setRevision(0); archive.setIdempotencyKey("validation-test"); archive.setChanges(values);
            for (var request : List.of(submit, draft, archive)) assertTrue(validator.validate(request).isEmpty());
            values.put("unknown", ""); draftValues.put("unknown", "");
            for (var request : List.of(submit, draft)) assertFalse(validator.validate(request).isEmpty());
            assertTrue(validator.validate(archive).isEmpty());
            values.put("one_more", "");
            assertFalse(validator.validate(archive).isEmpty());
        }
    }
}
