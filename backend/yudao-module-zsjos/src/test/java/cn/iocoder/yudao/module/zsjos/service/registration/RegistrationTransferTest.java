package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.RegistrationController;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegistrationTransferTest {
    @Test void transferIsNotAnAvailableCommand() {
        var retired = Set.of("transfer", "getTransferCandidates", "transferCandidates");
        for (Class<?> boundary : Set.of(RegistrationController.class, RegistrationService.class)) {
            assertFalse(Arrays.stream(boundary.getDeclaredMethods()).anyMatch(method -> retired.contains(method.getName())));
        }
    }
}
