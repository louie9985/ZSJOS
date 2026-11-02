package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoTaskVO.Query;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentInfoTaskQueryTest {
    @Test void acceptsEveryFilterAndRejectsUnknownStatus() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var query = new Query();
            assertTrue(validator.validateProperty(query, "status").isEmpty());
            for (String status : new String[]{"PENDING", "UNSENT", "DRAFT", "SUBMITTED", "REUSED", "CANCELLED"}) {
                query.setStatus(status);
                assertTrue(validator.validateProperty(query, "status").isEmpty(), status);
            }
            query.setStatus("UNKNOWN");
            assertFalse(validator.validateProperty(query, "status").isEmpty());
        }
    }
}
