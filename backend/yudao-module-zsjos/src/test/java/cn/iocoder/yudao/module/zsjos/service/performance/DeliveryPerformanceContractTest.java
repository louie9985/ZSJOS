package cn.iocoder.yudao.module.zsjos.service.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPerformanceContractTest {
 @Test void deliveryPermissionAndDomainAreIndependentFromSales() {
  String previous=PerformanceDomain.enter("delivery");
  try {
  String sales="zsjos:sales-performance-target:update";
  assertEquals("zsjos:delivery-performance-target:update",PerformanceDomain.permission(sales));
  assertEquals("zsjos:delivery-performance-target:configure",PerformanceDomain.permission("zsjos:sales-performance-target:configure"));
  assertNotEquals("zsjos:sales-performance:query",PerformanceDomain.permission("zsjos:sales-performance:query"));
  } finally {PerformanceDomain.restore(previous);}
 }
}
