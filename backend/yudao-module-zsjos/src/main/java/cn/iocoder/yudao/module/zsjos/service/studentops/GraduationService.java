package cn.iocoder.yudao.module.zsjos.service.studentops;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentops.vo.GraduationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentops.vo.GraduationRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentops.GraduationApplicationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentops.GraduationApplicationMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.PROCESS_KEY_GRADUATION;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class GraduationService {
    @Resource private GraduationApplicationMapper mapper; @Resource private ServiceRelationMapper relationMapper;
    @Resource private MediaAccountMapper accountMapper; @Resource private AdminUserApi adminUserApi; @Resource private DeptApi deptApi;
    @Resource private BpmProcessInstanceApi processInstanceApi; @Resource private MediaWorkflowEventService events;

    public List<GraduationRespVO> list(Long userId){return mapper.selectByParticipant(userId).stream().map(x->BeanUtils.toBean(x,GraduationRespVO.class)).toList();}

    @Transactional(rollbackFor=Exception.class)
    public Long create(GraduationCreateReqVO req,Long userId){
        ServiceRelationDO relation=relationMapper.selectById(req.getServiceRelationId());
        if(relation==null||!"active".equals(relation.getStatus())||!Objects.equals(relation.getOwnerUserId(),userId))throw exception(MEDIA_GRADUATION_STATE_INVALID);
        Long reviewer=requireSupervisor(userId); MediaAccountDO account=accountMapper.selectOne(new LambdaQueryWrapper<MediaAccountDO>().eq(MediaAccountDO::getStudentPersonId,relation.getPersonId()).orderByDesc(MediaAccountDO::getId).last("LIMIT 1"));
        GraduationApplicationDO row=new GraduationApplicationDO();row.setApplicationNo("GR-"+UUID.randomUUID().toString().replace("-","").substring(0,16));row.setServiceRelationId(relation.getId());row.setStudentPersonId(relation.getPersonId());row.setPlannerUserId(userId);row.setReviewerUserId(reviewer);row.setDirectorUserId(relation.getContentDirectorUserId());row.setOperatorUserId(account==null?null:account.getOwnerOperatorUserId());row.setReason(req.getReason().trim());row.setSnapshotJson(req.getSnapshotJson());row.setStatus("starting");row.setSubmittedAt(LocalDateTime.now());row.setVersion(0);mapper.insert(row);
        BpmProcessInstanceCreateReqDTO process=new BpmProcessInstanceCreateReqDTO();process.setProcessDefinitionKey(PROCESS_KEY_GRADUATION);process.setBusinessKey("media-graduation:"+row.getId());process.setVariables(new HashMap<>(Map.of("applicationId",row.getId(),"serviceRelationId",relation.getId(),"studentPersonId",relation.getPersonId(),"assignee",reviewer,"coll_userList",List.of(reviewer))));process.setStartUserSelectAssignees(Map.of("graduationReviewer",List.of(reviewer)));
        String processId;try{processId=processInstanceApi.createProcessInstance(userId,process);}catch(RuntimeException ex){throw exception(MEDIA_GRADUATION_PROCESS_UNAVAILABLE);}
        row.setProcessInstanceId(processId);row.setStatus("pending");mapper.updateById(row);events.transition("media-graduation",row.getId(),userId,"starting","pending",null,"media-graduation:"+row.getId()+":pending");return row.getId();
    }

    @Transactional(rollbackFor=Exception.class)
    public void handleProcessResult(String processId,Integer status,String reason){
        GraduationApplicationDO row=mapper.selectByProcessId(processId);if(row==null||!"pending".equals(row.getStatus()))return;
        String target=BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(status)?"approved":BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(status)?"rejected":null;if(target==null)return;
        row.setStatus(target);row.setResultReason(reason);row.setCompletedAt(LocalDateTime.now());row.setVersion(row.getVersion()+1);mapper.updateById(row);events.transition("media-graduation",row.getId(),row.getReviewerUserId(),"pending",target,reason,"media-graduation:"+row.getId()+":"+target);
        Map<String,Object> values=new LinkedHashMap<>();values.put("bizNo",row.getApplicationNo());values.put("deepLink","/zsjos/student-ops?graduationId="+row.getId());if(reason!=null)values.put("reason",reason);
        notifyResult(row,row.getPlannerUserId(),target+":planner",values);notifyResult(row,row.getDirectorUserId(),target+":director",values);notifyResult(row,row.getOperatorUserId(),target+":operator",values);
    }
    private void notifyResult(GraduationApplicationDO row,Long recipient,String suffix,Map<String,Object> values){if(recipient!=null)events.notify("media.graduation.result","media-graduation",row.getId(),recipient,row.getReviewerUserId(),"media-graduation-result:"+row.getId()+":"+suffix,values);}
    private Long requireSupervisor(Long uid){var user=adminUserApi.getUser(uid);var dept=user==null||user.getDeptId()==null?null:deptApi.getDept(user.getDeptId());Long leader=dept==null?null:dept.getLeaderUserId();var sup=leader==null?null:adminUserApi.getUser(leader);if(sup==null||Objects.equals(uid,leader)||!CommonStatusEnum.ENABLE.getStatus().equals(sup.getStatus()))throw exception(MEDIA_GRADUATION_SUPERVISOR_INVALID);return leader;}
}
