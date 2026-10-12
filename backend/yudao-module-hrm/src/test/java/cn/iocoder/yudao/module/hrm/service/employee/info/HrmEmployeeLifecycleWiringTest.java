package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.yudao.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.yudao.module.hrm.service.recruit.candidate.HrmRecruitCandidateServiceImpl;
import cn.iocoder.yudao.module.hrm.service.recruit.config.HrmRecruitChannelService;
import cn.iocoder.yudao.module.hrm.service.recruit.config.HrmRecruitChannelServiceImpl;
import cn.iocoder.yudao.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.yudao.module.hrm.service.recruit.post.HrmRecruitPostServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrmEmployeeLifecycleWiringTest {
    @Test
    void employeeFirstStartupAndDeferredRecruitmentUseFinalEmployeeProxy() {
        try (var context = new AnnotationConfigApplicationContext()) {
            // The isolated context has fewer proxy processors than the server. Reject early
            // circular references so a raw employee instance cannot conceal this dependency cycle.
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.registerBean(MethodValidationPostProcessor.class, () -> {
                var processor = new MethodValidationPostProcessor();
                processor.setProxyTargetClass(true);
                return processor;
            });
            // Start with the employee bean, as the EAM -> HRM API dependency does in the server.
            Map<Class<?>, Class<?>> realServices = new LinkedHashMap<>();
            realServices.put(HrmEmployeeService.class, HrmEmployeeServiceImpl.class);
            realServices.put(HrmEmployeeManualLifecycle.class, HrmEmployeeManualLifecycle.class);
            realServices.put(HrmRecruitCandidateService.class, HrmRecruitCandidateServiceImpl.class);
            realServices.put(HrmRecruitPostService.class, HrmRecruitPostServiceImpl.class);
            realServices.put(HrmRecruitChannelService.class, HrmRecruitChannelServiceImpl.class);
            realServices.values().forEach(context::register);

            // Mock only dependencies outside the cycle; retain production fields and @Resource/@Lazy wiring.
            var externalTypes = new HashSet<Class<?>>();
            for (Class<?> implementation : realServices.values()) {
                for (var field : implementation.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Resource.class) && !realServices.containsKey(field.getType())
                            && externalTypes.add(field.getType())) {
                        registerMock(context, field.getType());
                    }
                }
            }
            context.refresh();

            var employee = context.getBean(HrmEmployeeService.class);
            assertTrue(AopUtils.isAopProxy(employee), "Employee method validation must remain active");
            for (Class<?> consumer : new Class<?>[]{HrmRecruitCandidateService.class,
                    HrmRecruitPostService.class, HrmRecruitChannelService.class}) {
                assertSame(employee, ReflectionTestUtils.getField(context.getBean(consumer), "employeeService"),
                        "Recruitment must use the final employee proxy, not an early raw instance");
            }
            var lifecycle = context.getBean(HrmEmployeeManualLifecycle.class);
            var candidates = (HrmRecruitCandidateService) ReflectionTestUtils.getField(lifecycle, "candidates");
            assertNotNull(candidates);
            assertNull(candidates.getRecruitCandidate(123L));
            verify(context.getBean(HrmRecruitCandidateMapper.class)).selectById(123L);
        }
    }

    private static <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }
}
