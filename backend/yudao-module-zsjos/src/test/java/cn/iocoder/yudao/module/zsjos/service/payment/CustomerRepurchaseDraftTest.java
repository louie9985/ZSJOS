package cn.iocoder.yudao.module.zsjos.service.payment;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentSaveDraftReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.RepurchaseCustomerCheckRespVO;
import cn.iocoder.yudao.module.zsjos.service.order.RepurchaseCustomerService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PurchaseIntentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentIntentMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PurchaseIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerRepurchaseDraftTest {
 @InjectMocks PurchaseIntentService service;
 @Mock RepurchaseCustomerService repurchaseCustomers;
 @Mock PurchaseIntentMapper purchaseIntentMapper;
 @Mock PaymentIntentMapper paymentIntentMapper;
 @Mock PersonIdentityWriteService personIdentityWriteService;
 private PurchaseIntentSaveDraftReqVO request(){var r=new PurchaseIntentSaveDraftReqVO();r.setPurchaseType("customer_repurchase");r.setSourceKey("customer:10");r.setDraft(Map.of("repurchaseIdentity",Map.of("customerName","测试","customerMobile","13800138000")));return r;}
 private void allowed(){var r=new RepurchaseCustomerCheckRespVO();r.setCanRepurchase(true);r.setPersonId(10L);when(repurchaseCustomers.checkCustomer(eq(20L),any())).thenReturn(r);}
 @Test void exactIdentityResolvesPersonBeforeReadingOwnDraft(){allowed();assertNull(service.current(request(),20L));verify(purchaseIntentMapper).selectActive(null,10L,"customer_repurchase","customer:10",20L);}
 @Test void cannotForgeDifferentPersonId(){allowed();var r=request();r.setPersonId(11L);assertThrows(RuntimeException.class,()->service.current(r,20L));verifyNoInteractions(purchaseIntentMapper);}
 @Test void cannotAttachLeadOrOpportunity(){var r=request();r.setLeadId(1L);assertThrows(RuntimeException.class,()->service.current(r,20L));verifyNoInteractions(purchaseIntentMapper,repurchaseCustomers);}
 @Test void missingIdentityRejectedEvenWithPersonId(){var r=request();r.setDraft(Map.of());r.setPersonId(10L);assertThrows(RuntimeException.class,()->service.current(r,20L));verifyNoInteractions(purchaseIntentMapper);}
 @Test void deniedPreflightCannotReadDraft(){when(repurchaseCustomers.checkCustomer(eq(20L),any())).thenReturn(new RepurchaseCustomerCheckRespVO());assertThrows(RuntimeException.class,()->service.current(request(),20L));verifyNoInteractions(purchaseIntentMapper);}

 private PurchaseIntentSaveDraftReqVO saveRequest() {
  var r = request();
  r.setCollectionMode("offline_paid"); r.setIdempotencyKey("repurchase-draft-test");
  var item = new PurchaseIntentSaveDraftReqVO.Item();
  item.setSpuRef("course"); item.setSkuRef("plan"); item.setActualAmount(BigDecimal.TEN);
  r.setItems(List.of(item)); r.setTotalAmount(BigDecimal.TEN);
  return r;
 }

 @Test void savesMatchedCustomerDraftUnderCurrentSubmitter() {
  allowed();
  doAnswer(call -> { ((PurchaseIntentDO) call.getArgument(0)).setId(9L); return 1; })
    .when(purchaseIntentMapper).insert(any(PurchaseIntentDO.class));
  assertEquals(9L, service.saveDraft(saveRequest(), 20L).getId());
  var captured = ArgumentCaptor.forClass(PurchaseIntentDO.class);
  verify(purchaseIntentMapper).insert(captured.capture());
  assertEquals(10L, captured.getValue().getPersonId());
  assertEquals(20L, captured.getValue().getInitiatorUserId());
  assertEquals(20L, captured.getValue().getOwnerUserId());
  assertEquals("customer_repurchase", captured.getValue().getPurchaseType());
  assertNull(captured.getValue().getLeadId());
  assertNull(captured.getValue().getOpportunityId());
  verifyNoInteractions(personIdentityWriteService);
 }

 @Test void createsUnknownCustomerThroughIdentityReservationBeforeSaving() {
  var checked = new RepurchaseCustomerCheckRespVO(); checked.setCanRepurchase(true);
  when(repurchaseCustomers.checkCustomer(eq(20L), any())).thenReturn(checked);
  var person = new PersonDO(); person.setId(10L);
  when(personIdentityWriteService.resolveOrCreate(anyString(), anyString(), isNull(), eq("active"))).thenReturn(person);
  doAnswer(call -> { ((PurchaseIntentDO) call.getArgument(0)).setId(9L); return 1; })
    .when(purchaseIntentMapper).insert(any(PurchaseIntentDO.class));
  assertEquals(10L, service.saveDraft(saveRequest(), 20L).getPersonId());
  var sequence = inOrder(personIdentityWriteService, repurchaseCustomers, purchaseIntentMapper);
  sequence.verify(personIdentityWriteService).resolveOrCreate(anyString(), anyString(), isNull(), eq("active"));
  sequence.verify(repurchaseCustomers).requireIdentity(eq(10L), any());
  sequence.verify(purchaseIntentMapper).insert(any(PurchaseIntentDO.class));
 }

 @Test void deniedPreflightCannotSaveOrCreateCustomer() {
  when(repurchaseCustomers.checkCustomer(eq(20L), any())).thenReturn(new RepurchaseCustomerCheckRespVO());
  assertThrows(RuntimeException.class, () -> service.saveDraft(saveRequest(), 20L));
  verifyNoInteractions(purchaseIntentMapper, personIdentityWriteService, paymentIntentMapper);
 }
}
