package cn.iocoder.yudao.module.zsjos.service.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceDomainTest {
 @Test void nestedDomainRestoresOnExitAndUsesIndependentPermissions() {
  assertEquals("sales",PerformanceDomain.current());
  var previous=PerformanceDomain.enter("delivery");
  try {
   assertEquals("zsjos:delivery-performance-target:update",PerformanceDomain.permission("zsjos:sales-performance-target:update"));
   var nested=PerformanceDomain.enter("sales");
   try {assertEquals("sales",PerformanceDomain.current());} finally {PerformanceDomain.restore(nested);}
   assertEquals("delivery",PerformanceDomain.current());
  } finally {PerformanceDomain.restore(previous);}
  assertEquals("sales",PerformanceDomain.current());
 }
 @Test void controllerBoundariesCannotBeSelectedByRequestParameters() {
  assertEquals("delivery",cn.iocoder.yudao.module.zsjos.controller.admin.performance.DeliveryPerformanceController.class.getAnnotation(PerformanceDomainBoundary.class).value());
  assertEquals("sales",cn.iocoder.yudao.module.zsjos.controller.admin.performance.SalesPerformanceController.class.getAnnotation(PerformanceDomainBoundary.class).value());
 }
 @PerformanceDomainBoundary("delivery")
 static class Endpoint { public String read(){return PerformanceDomain.current();} }
 @Test void springProxyBindsDomainAndCleansUpAfterCall() {
  var factory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(new Endpoint());
  factory.addAspect(new PerformanceDomainAspect());
  Endpoint proxy=factory.getProxy();assertEquals("delivery",proxy.read());assertEquals("sales",PerformanceDomain.current());
 }
}
