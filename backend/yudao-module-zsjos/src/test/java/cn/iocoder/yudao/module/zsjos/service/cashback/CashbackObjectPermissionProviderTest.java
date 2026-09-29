package cn.iocoder.yudao.module.zsjos.service.cashback;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class CashbackObjectPermissionProviderTest {
 @InjectMocks CashbackObjectPermissionProvider provider;
 @Mock CashbackMapper mapper; @Mock PermissionApi permissions;
 @Test void queryAloneDoesNotGrantMutation() {
  when(mapper.selectById(1L)).thenReturn(new CashbackDO());
  when(permissions.hasAnyPermissions(2L,"zsjos:cashback:finance-query")).thenReturn(true);
  assertTrue(provider.hasPermission(1L,"read",2L)); assertFalse(provider.hasPermission(1L,"block",2L)); assertFalse(provider.hasPermission(1L,"unblock",2L));
 }
 @Test void mutationRequiresQueryAndMatchingButton() {
  when(mapper.selectById(1L)).thenReturn(new CashbackDO());
  when(permissions.hasAnyPermissions(2L,"zsjos:cashback:finance-query")).thenReturn(true);
  when(permissions.hasAnyPermissions(2L,"zsjos:cashback:block")).thenReturn(true);
  assertTrue(provider.hasPermission(1L,"block",2L)); assertFalse(provider.hasPermission(1L,"unblock",2L));
 }
 @Test void missingOrTenantFilteredObjectIsDenied() { assertFalse(provider.hasPermission(1L,"block",2L)); verifyNoInteractions(permissions); }
 @Test void unknownActionIsDenied() { when(mapper.selectById(1L)).thenReturn(new CashbackDO()); assertFalse(provider.hasPermission(1L,"other",2L)); }
}
