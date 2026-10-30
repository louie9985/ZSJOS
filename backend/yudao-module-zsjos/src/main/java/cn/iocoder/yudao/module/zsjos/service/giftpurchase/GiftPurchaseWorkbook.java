package cn.iocoder.yudao.module.zsjos.service.giftpurchase;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
final class GiftPurchaseWorkbook {
 private GiftPurchaseWorkbook() {}
 static byte[] write(List<GiftPurchaseRespVO> purchases, Map<Long,List<GiftPurchaseOperationRespVO>> history) {
  var rows = new ArrayList<List<String>>(); var operations = new ArrayList<List<String>>(); int sequence=0;
  for(var r:purchases) {
   String number=Integer.toString(++sequence);
   String gifts=r.isGiftItemsInvalid()?"历史礼品信息无法读取":r.getGiftItemSnapshots()==null || r.getGiftItemSnapshots().isEmpty()?"无礼品":r.getGiftItemSnapshots().stream().map(g->historical(g.name())).collect(Collectors.joining("、"));
   rows.add(List.of(number,r.isPurchased()?"已采购":"未采购",text(r.getStudentName()),text(r.getStudentMobile()),text(r.getStudentWechatId()),gifts,text(r.getShippingAddress()),"ORDER".equals(r.getSourceType())?"订单提交":"MANUAL".equals(r.getSourceType())?"手工创建":"来源待确认",text(r.getOrderNo()),historical(r.getSourceUserName()),time(r.getGeneratedAt()),r.isPurchased()?historical(r.getPurchasedByName()):"-",time(r.getPurchasedAt())));
   for(var op:history.getOrDefault(r.getId(),List.of())) operations.add(List.of(number,text(r.getStudentName()),text(r.getOrderNo()),action(op.getAction()),historical(op.getOperatorName()),time(op.getOccurredAt())));
  }
  var output=new ByteArrayOutputStream();
  // String cells retain phone numbers and prevent formula interpretation of user text.
  try(var writer=FastExcelFactory.write(output).autoCloseStream(false).build()) {
   writer.write(rows,FastExcelFactory.writerSheet(0,"采购清单").head(head("导出序号","采购状态","学员姓名","手机号","微信号","礼品","邮寄地址","采购来源","订单号","来源人","生成时间","标记已采购人","采购标记时间")).build());
   writer.write(operations,FastExcelFactory.writerSheet(1,"操作记录").head(head("导出序号","学员姓名","订单号","操作","操作人","操作时间")).build());
  }
  return output.toByteArray();
 }
 private static List<List<String>> head(String... names){return Arrays.stream(names).map(List::of).toList();}
 private static String text(String v){return v==null?"":v;}
 private static String historical(String v){return v==null || v.isBlank()?"历史未记录":v;}
 private static String time(LocalDateTime v){return v==null?"":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(v);}
 private static String action(String v){if(v==null)return "历史未记录";return switch(v){case "CREATE"->"新增采购";case "GENERATE"->"订单自动生成";case "UPDATE"->"编辑采购";case "PURCHASE"->"标记已采购";case "UNPURCHASE"->"标记未采购";case "DELETE"->"删除采购";default->v;};}
}
