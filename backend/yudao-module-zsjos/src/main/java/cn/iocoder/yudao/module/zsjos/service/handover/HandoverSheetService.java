package cn.iocoder.yudao.module.zsjos.service.handover;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.handover.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.handover.HandoverSheetDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.handover.HandoverSheetMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class HandoverSheetService {
    @Resource private HandoverSheetMapper mapper; @Resource private MediaWorkflowEventService events;
    @Resource private AdminUserApi adminUserApi; @Resource private DeptApi deptApi;
    @Resource private MediaAccountMapper accountMapper; @Resource private ContentMapper contentMapper;
    @Resource private ProductionTicketMapper ticketMapper; @Resource private PositioningCardMapper positioningCardMapper;
    public List<HandoverSheetRespVO> list(Long userId){return mapper.selectByParticipant(userId).stream().map(x->toResp(x,userId)).toList();}
    @Transactional(rollbackFor=Exception.class) public Long create(HandoverSheetCreateReqVO req,Long userId){
        if(Objects.equals(req.getFromUserId(),req.getToUserId())||!Objects.equals(req.getFromUserId(),userId))throw exception(HANDOVER_PERMISSION_DENIED);
        adminUserApi.validateUser(req.getFromUserId());adminUserApi.validateUser(req.getToUserId());
        boolean exists=switch(req.getBizType()){case "media-account"->accountMapper.selectById(req.getBizId())!=null;case "content"->contentMapper.selectById(req.getBizId())!=null;case "production-ticket"->ticketMapper.selectById(req.getBizId())!=null;case "positioning-card"->positioningCardMapper.selectById(req.getBizId())!=null;default->false;};
        if(!exists)throw exception(HANDOVER_NOT_EXISTS); HandoverSheetDO x=new HandoverSheetDO();
        x.setHandoverNo("HO-"+UUID.randomUUID().toString().replace("-","").substring(0,16));x.setBizType(req.getBizType());x.setBizId(req.getBizId());x.setFromUserId(userId);x.setToUserId(req.getToUserId());x.setChecklistJson(req.getChecklistJson());x.setStatus("pending_accept");x.setVersion(0);mapper.insert(x);
        events.createTaskAndNotify("media.handover.pending_accept","MEDIA_HANDOVER_ACCEPT","handover",x.getId(),x.getToUserId(),"交接单待接收","accept",userId,"handover-accept:"+x.getId(),payload(x));return x.getId();}
    @ZsjosPermission(bizType="handover",bizId="#id",action="accept") @Transactional(rollbackFor=Exception.class) public void accept(Long id,Integer version,boolean partial){HandoverSheetDO c=require(id);if(!List.of("pending_accept","partial_received").contains(c.getStatus())||(partial&&"partial_received".equals(c.getStatus())))throw exception(HANDOVER_STATE_INVALID);String t=partial?"partial_received":"all_received";transition(c,version,t,c.getToUserId(),null);HandoverSheetDO u=new HandoverSheetDO();u.setId(id).setAcceptedAt(LocalDateTime.now()).setResponsibilityStartedAt(partial?null:LocalDateTime.now());mapper.updateById(u);if(!partial){events.completeTask("MEDIA_HANDOVER_ACCEPT",id,c.getToUserId());notifyResult("media.handover.accepted",c,c.getFromUserId(),"accepted",null);}}
    @ZsjosPermission(bizType="handover",bizId="#id",action="reject") @Transactional(rollbackFor=Exception.class) public void reject(Long id,Integer version,String reason){HandoverSheetDO c=require(id);if(!List.of("pending_accept","partial_received").contains(c.getStatus()))throw exception(HANDOVER_STATE_INVALID);transition(c,version,"rejected",c.getToUserId(),reason);HandoverSheetDO u=new HandoverSheetDO();u.setId(id).setRejectReason(reason);mapper.updateById(u);events.completeTask("MEDIA_HANDOVER_ACCEPT",id,c.getToUserId());notifyResult("media.handover.rejected",c,c.getFromUserId(),"rejected",reason);}
    @ZsjosPermission(bizType="handover",bizId="#id",action="request-arbitration") @Transactional(rollbackFor=Exception.class) public void requestArbitration(Long id,Integer version,String reason,Long userId){HandoverSheetDO c=require(id);if(!"rejected".equals(c.getStatus())||!Objects.equals(c.getFromUserId(),userId))throw exception(HANDOVER_STATE_INVALID);Long arb=requireSupervisor(userId);transition(c,version,"arbitration_pending",userId,reason);HandoverSheetDO u=new HandoverSheetDO();u.setId(id).setArbitrationApplicantUserId(userId).setArbitratorUserId(arb).setArbitrationReason(reason);mapper.updateById(u);events.createTaskAndNotify("media.handover.arbitration_pending","MEDIA_HANDOVER_ARBITRATION","handover",id,arb,"交接仲裁待处理","arbitrate",userId,"handover-arbitration:"+id+":"+version,payload(c));}
    @ZsjosPermission(bizType="handover",bizId="#id",action="arbitrate") @Transactional(rollbackFor=Exception.class) public void arbitrate(Long id,Integer version,boolean accept,String reason,Long userId){HandoverSheetDO c=require(id);if(!"arbitration_pending".equals(c.getStatus())||!Objects.equals(c.getArbitratorUserId(),userId))throw exception(HANDOVER_STATE_INVALID);String t=accept?"all_received":"arbitration_terminated";transition(c,version,t,userId,reason);HandoverSheetDO u=new HandoverSheetDO();u.setId(id).setArbitrationDecision(accept?"accept":"terminate").setArbitrationAt(LocalDateTime.now());if(accept)u.setResponsibilityStartedAt(LocalDateTime.now());mapper.updateById(u);events.completeTask("MEDIA_HANDOVER_ARBITRATION",id,userId);notifyResult("media.handover.arbitration_resolved",c,c.getFromUserId(),t+":from",reason);notifyResult("media.handover.arbitration_resolved",c,c.getToUserId(),t+":to",reason);}
    private void transition(HandoverSheetDO c,Integer v,String t,Long op,String reason){if(mapper.transition(c.getId(),v,c.getStatus(),t)==0)throw exception(HANDOVER_VERSION_CONFLICT);events.transition("handover",c.getId(),op,c.getStatus(),t,reason,"handover:"+c.getId()+":"+v+":"+t);}
    private Long requireSupervisor(Long uid){var user=adminUserApi.getUser(uid);var dept=user==null||user.getDeptId()==null?null:deptApi.getDept(user.getDeptId());Long leader=dept==null?null:dept.getLeaderUserId();var sup=leader==null?null:adminUserApi.getUser(leader);if(sup==null||Objects.equals(uid,leader)||!CommonStatusEnum.ENABLE.getStatus().equals(sup.getStatus()))throw exception(HANDOVER_SUPERVISOR_INVALID);return leader;}
    private void notifyResult(String scene,HandoverSheetDO c,Long recipient,String suffix,String reason){Map<String,Object> v=new LinkedHashMap<>(payload(c));if(reason!=null)v.put("reason",reason);events.notify(scene,"handover",c.getId(),recipient,c.getToUserId(),"handover-result:"+c.getId()+":"+suffix,v);}
    private Map<String,Object> payload(HandoverSheetDO x){return Map.of("bizNo",x.getHandoverNo(),"deepLink","/zsjos/handovers?handoverId="+x.getId());}
    private HandoverSheetDO require(Long id){HandoverSheetDO x=mapper.selectById(id);if(x==null)throw exception(HANDOVER_NOT_EXISTS);return x;}
    private HandoverSheetRespVO toResp(HandoverSheetDO x,Long uid){HandoverSheetRespVO v=BeanUtils.toBean(x,HandoverSheetRespVO.class);if(List.of("pending_accept","partial_received").contains(x.getStatus())&&Objects.equals(x.getToUserId(),uid))v.setAvailableActions("partial_received".equals(x.getStatus())?List.of("accept"):List.of("accept","reject"));else if("rejected".equals(x.getStatus())&&Objects.equals(x.getFromUserId(),uid))v.setAvailableActions(List.of("request-arbitration"));else if("arbitration_pending".equals(x.getStatus())&&Objects.equals(x.getArbitratorUserId(),uid))v.setAvailableActions(List.of("arbitrate-accept","arbitrate-terminate"));else v.setAvailableActions(List.of());return v;}
}
