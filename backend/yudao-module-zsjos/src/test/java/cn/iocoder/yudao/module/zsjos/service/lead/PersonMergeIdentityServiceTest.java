package cn.iocoder.yudao.module.zsjos.service.lead;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class PersonMergeIdentityServiceTest {
    @InjectMocks PersonMergeIdentityService service;
    @Mock PersonMapper mapper;
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void untouchedIdentityStaysUnchanged(){when(mapper.selectMergedTarget(8L,1L)).thenReturn(null);assertEquals(8L,service.resolve(8L));verify(mapper).selectMergedTarget(8L,1L);}
    @Test void followsHistoricalChainInCurrentTenant(){when(mapper.selectMergedTarget(8L,1L)).thenReturn(9L);when(mapper.selectMergedTarget(9L,1L)).thenReturn(10L);when(mapper.selectMergedTarget(10L,1L)).thenReturn(null);assertEquals(10L,service.resolve(8L));}
    @Test void rejectsCycle(){when(mapper.selectMergedTarget(8L,1L)).thenReturn(9L);when(mapper.selectMergedTarget(9L,1L)).thenReturn(8L);assertThrows(RuntimeException.class,()->service.resolve(8L));}
    @Test void mappingIsNotAnAuthorizationGrant(){TenantContextHolder.setTenantId(2L);when(mapper.selectMergedTarget(8L,2L)).thenReturn(null);assertEquals(8L,service.resolve(8L));verify(mapper).selectMergedTarget(8L,2L);verify(mapper,never()).selectMergedTarget(8L,1L);}
}
