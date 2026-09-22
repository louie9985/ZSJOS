package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceAttributionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceAttributionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service
public class PerformanceSnapshotService {
 @Resource private PerformanceAttributionMapper mapper;
 @Resource private PerformanceAccess access;
 @Resource private LeadMapper leadMapper;
 public void activity(String type,Long factId,Long leadId,Long userId) {
  if(factId==null||userId==null)return;insert(base(type,factId,userId,leadMapper.selectById(leadId)));
 }
 public void qualification(LeadDO lead,Long taskId,Long userId) {
  if(taskId==null)return;var row=base("QUALIFICATION",taskId,userId,lead);row.setOutcome("pending");insert(row);
 }
 public void qualificationResult(Long taskId,String outcome,LocalDateTime at) {
  if(taskId==null)return;var row=mapper.selectOne(new LambdaQueryWrapperX<PerformanceAttributionDO>().eq(PerformanceAttributionDO::getFactType,"QUALIFICATION").eq(PerformanceAttributionDO::getFactId,taskId));
  if(row!=null){row.setOutcome(outcome);row.setCompletedAt(at);mapper.updateById(row);}
 }
 public void order(SalesOrderDO order) {
  LeadDO lead=order.getLeadId()==null?null:leadMapper.selectById(order.getLeadId());
  var row=base("ORDER",order.getId(),order.getFormalSalesUserId(),lead);
  if("repurchase".equals(order.getOrderType()))row.setSourceGroup("repurchase");
  insert(row);
 }
 public void received(Long leadId,Long userId,Long assignmentId,LocalDateTime at) {
  var lead=leadMapper.selectById(leadId);if(lead==null)return;
  var row=base("ASSIGNMENT",assignmentId,userId,lead);row.setReceivedAt(at);row.setAssignmentId(assignmentId);insert(row);
 }
 private PerformanceAttributionDO base(String type,Long id,Long userId,LeadDO lead) {
  var row=new PerformanceAttributionDO();row.setFactType(type);row.setFactId(id);row.setUserId(userId);
  var user=userId==null?null:access.user(userId);
  if(user!=null){row.setUserName(user.getNickname());row.setDeptId(user.getDeptId());var dept=access.dept(user.getDeptId());if(dept!=null)row.setDeptName(dept.getName());var m=access.mapping(user.getDeptId());if(m!=null){row.setCenterId(m.getCenterId());var c=access.dept(m.getCenterId());if(c!=null)row.setCenterName(c.getName());}}
  if(lead!=null){row.setLeadId(lead.getId());row.setAssignmentId(lead.getCurrentAssignmentHistoryId());row.setReceivedAt(lead.getOwnershipStartedAt());row.setChannelCode(lead.getSourceChannelId());row.setChannelLabel(lead.getSourceChannelLabelSnapshot());row.setSourceGroup(switch(lead.getSourceType()==null?"":lead.getSourceType()){case "internal_new_media","partner"->"inbound";case "sales_self_sourced"->"self";default->"unknown";});}
  else row.setSourceGroup("unknown");
  return row;
 }
 private void insert(PerformanceAttributionDO row) {
  if(mapper.selectCount(new LambdaQueryWrapperX<PerformanceAttributionDO>().eq(PerformanceAttributionDO::getFactType,row.getFactType()).eq(PerformanceAttributionDO::getFactId,row.getFactId()))==0)mapper.insert(row);
 }
}
