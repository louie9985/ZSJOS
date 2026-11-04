package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class StudentInfoFormInjectionTest {
    @Test
    void selectsStudentServiceWhenSystemBeanHasTheFieldName() {
        var factory = new DefaultListableBeanFactory();
        var expected = mock(StudentIdentityAreaService.class);
        // System owns this bean name; it must not shadow the student-specific service.
        factory.registerSingleton("identityAreaService", new Object());
        factory.registerSingleton("studentIdentityAreaService", expected);
        for (var field : StudentInfoFormService.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Resource.class) && !field.getName().equals("identityAreaService")) {
                factory.registerSingleton(field.getName(), mock(field.getType()));
            }
        }
        var processor = new CommonAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        var service = new StudentInfoFormService();
        processor.processInjection(service);
        assertSame(expected, ReflectionTestUtils.getField(service, "identityAreaService"));
    }
}
