package cn.iocoder.yudao.module.zsjos.service.giftpurchase;
import cn.iocoder.yudao.module.zsjos.service.gift.GiftConfigService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderGiftSnapshot;
import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GiftCatalogTreeTest {
 @Test void actualTreeShapeOffersAndValidatesLeavesWithFullPath(){
  var service=mock(GiftConfigService.class);var root=new GiftConfigDO();root.setId(1L);root.setCode("category");root.setName("Category");root.setStatus(0);
  var leaf=new GiftConfigDO();leaf.setId(2L);leaf.setParentId(1L);leaf.setCode("leaf");leaf.setName("Leaf");leaf.setStatus(0);root.setChildren(List.of(leaf));
  when(service.list(null)).thenReturn(List.of(root));doCallRealMethod().when(service).listFlat(null);
  var flat=service.listFlat(null);assertEquals(2,flat.size());
  assertEquals(List.of("Category","Leaf"),SalesOrderGiftSnapshot.resolve(List.of("leaf"),null,()->flat,LocalDateTime.now()).getFirst().path());
  assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->SalesOrderGiftSnapshot.resolve(List.of("category"),null,()->flat,LocalDateTime.now()));
 }
}
