package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import cn.iocoder.yudao.module.zsjos.framework.permission.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PartnerStudentManualLinkServiceTest {
    PartnerStudentManualLinkService service;
    PartnerStudentLinkService links = mock(PartnerStudentLinkService.class);
    PartnerStudentLinkMapper mapper = mock(PartnerStudentLinkMapper.class);
    PartnerMapper partners = mock(PartnerMapper.class);
    PersonMapper people = mock(PersonMapper.class);
    ZsjosObjectPermissionProvider permission = mock(ZsjosObjectPermissionProvider.class);
    PersonDO person = new PersonDO();
    PartnerDO partner = new PartnerDO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var target = new PartnerStudentManualLinkService();
        ReflectionTestUtils.setField(target, "links", links); ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "partners", partners); ReflectionTestUtils.setField(target, "people", people);
        when(permission.getBizType()).thenReturn("student");
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new ZsjosPermissionAspect(List.of(permission)));
        service = factory.getProxy();
        person.setTenantId(1L); partner.setTenantId(1L); partner.setPartnerNo("P-TEST"); partner.setName("测试兼职");
        when(people.selectById(2L)).thenReturn(person); when(partners.selectById(3L)).thenReturn(partner);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    PartnerStudentLinkDO link() {
        var link = new PartnerStudentLinkDO(); link.setPartnerId(3L); link.setTenantId(1L); link.setStartedAt(LocalDateTime.now());
        when(mapper.selectActiveByStudent(2L)).thenReturn(link); return link;
    }
    @Test void unbound() { assertFalse(service.getStudent(2L).isBound()); verify(permission).check(eq(2L), eq("read"), isNull()); }
    @Test void bound() { var link = link(); var result = service.getStudent(2L); assertTrue(result.isBound()); assertEquals("P-TEST", result.getPartnerNo()); assertEquals(link.getStartedAt(), result.getStartedAt()); }
    @Test void missingPartnerIsError() { link(); when(partners.selectById(3L)).thenReturn(null); assertThrows(RuntimeException.class, () -> service.getStudent(2L)); }
    @Test void crossTenantLinkIsError() { link().setTenantId(9L); assertThrows(RuntimeException.class, () -> service.getStudent(2L)); }
    @Test void crossTenantPartnerCannotReadOrBind() { link(); partner.setTenantId(9L); assertThrows(RuntimeException.class, () -> service.getStudent(2L)); assertThrows(RuntimeException.class, () -> service.bind(3L,2L,null,7L)); verifyNoInteractions(links); }
    @Test void crossTenantStudentCannotReadOrBind() { person.setTenantId(9L); assertThrows(RuntimeException.class, () -> service.getStudent(2L)); assertThrows(RuntimeException.class, () -> service.bind(3L,2L,null,7L)); verifyNoInteractions(links); }
    @Test void deniedStudentNeverWritesOrReadsStatus() {
        doThrow(new IllegalStateException("denied")).when(permission).check(eq(2L),eq("read"),isNull());
        assertThrows(IllegalStateException.class, () -> service.getStudent(2L));
        assertThrows(IllegalStateException.class, () -> service.bind(3L,2L,null,7L)); verifyNoInteractions(links, mapper, people, partners);
    }
    @Test void authorizedBindingPreservesWireValues() { service.bind(3L,2L,"checked",7L); verify(links).bind(3L,2L,"checked",7L); }
    @Test void bindingConflictIsNotSwallowed() { doThrow(new IllegalStateException("conflict")).when(links).bind(3L,2L,null,7L); assertThrows(IllegalStateException.class, () -> service.bind(3L,2L,null,7L)); }
}
