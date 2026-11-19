package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentInfoAddressRequiredTest {
    @Test void selectedAddressesAreRequiredWithoutChangingTemplate() {
        var template = new StudentInfoFields().presets();
        var resolved = new StudentInfoProductRules().resolve(template, List.of(), List.of());
        var selected = StudentInfoMaterialSelection.select(resolved, List.of("gift_shipping_address", "certificate_shipping_address"));
        for (String key : List.of("gift_shipping_address", "textbook_shipping_address")) {
            assertTrue(selected.stream().filter(f -> key.equals(f.getKey())).findFirst().orElseThrow().getRequired());
            assertFalse(template.stream().filter(f -> key.equals(f.getKey())).findFirst().orElseThrow().getRequired());
        }
        assertFalse(selected.stream().filter(f -> "certificate_shipping_address".equals(f.getKey())).findFirst().orElseThrow().getRequired());
        assertTrue(StudentInfoMaterialSelection.select(resolved, List.of()).stream().noneMatch(f -> "gift_shipping_address".equals(f.getKey())));
        template.stream().filter(f -> "textbook_shipping_address".equals(f.getKey())).forEach(f -> f.setEnabled(false));
        assertTrue(new StudentInfoProductRules().resolve(template, List.of(), List.of()).stream().noneMatch(f -> "textbook_shipping_address".equals(f.getKey())));
    }
}
