package cn.iocoder.yudao.module.zsjos.controller.admin.delivery;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliverySubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryDeferDO;
import cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryPlanService;
import cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliverySubmissionService;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliverySubmissionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryDeferReqVO;
import cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryDeferService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryPlanRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryConfigReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryConfigMapper;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/zsjos/student-delivery") @Validated
public class StudentDeliveryController {
    @Resource private StudentDeliveryPlanService planService;
    @Resource private StudentDeliverySubmissionService submissionService;
    @Resource private StudentDeliveryDeferService deferService;
    @Resource private StudentDeliveryPlanMapper planMapper;
    @Resource private StudentDeliveryStageMapper stageMapper;
    @Resource private StudentDeliveryConfigMapper configMapper;

    @GetMapping("/config")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery-config:query')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO> getConfig() { return success(configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>().eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled, true).orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getVersion).last("LIMIT 1"))); }

    @PutMapping("/config")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery-config:update')")
    public CommonResult<Boolean> updateConfig(@RequestBody @Validated StudentDeliveryConfigReqVO req) { var row=configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>().eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled,true).orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getVersion).last("LIMIT 1")); if(row==null) row=new cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO().setVersion(1).setEnabled(true); else row.setVersion(row.getVersion()+1); row.setS0Days(req.getS0Days()).setS1Days(req.getS1Days()).setS2Days(req.getS2Days()).setS3Days(req.getS3Days()).setS4Days(req.getS4Days()).setS5Days(req.getS5Days()).setS6Days(req.getS6Days()); configMapper.insert(row); return success(true); }

    @GetMapping("/plan")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:query')")
    public CommonResult<StudentDeliveryPlanRespVO> getPlan(@RequestParam Long accountId) {
        var plan = planMapper.selectActiveByAccountId(accountId); if (plan == null) return success(null);
        var vo = new StudentDeliveryPlanRespVO(); vo.setId(plan.getId()); vo.setAccountId(plan.getAccountId()); vo.setStatus(plan.getStatus()); vo.setAccountOpenedAt(plan.getAccountOpenedAt());
        vo.setStages(stageMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO>().eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO::getPlanId, plan.getId()).orderByAsc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO::getStageCode)).stream().map(stage -> { var s = new StudentDeliveryPlanRespVO.Stage(); s.setId(stage.getId()); s.setStageCode(stage.getStageCode()); s.setStatus(stage.getStatus()); s.setTriggerAt(stage.getTriggerAt()); s.setDueAt(stage.getDueAt()); s.setCompletedAt(stage.getCompletedAt()); return s; }).collect(Collectors.toList())); return success(vo);
    }

    @PostMapping("/defer")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:defer')")
    public CommonResult<StudentDeliveryDeferDO> defer(@RequestBody @Validated StudentDeliveryDeferReqVO req) { return success(deferService.request(req)); }

    @PostMapping("/submission")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:submit')")
    public CommonResult<StudentDeliverySubmissionDO> submit(@RequestBody @Validated StudentDeliverySubmissionReqVO req) {
        return success(submissionService.submit(req));
    }

    @PostMapping("/plan")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:create')")
    public CommonResult<StudentDeliveryPlanDO> ensurePlan(@RequestParam @NotNull Long studentPersonId,
                                                          @RequestParam @NotNull Long accountId,
                                                          @RequestParam(required = false) Long serviceRelationId,
                                                          @RequestParam(required = false) Long directorUserId,
                                                          @RequestParam LocalDateTime accountOpenedAt) {
        return success(planService.ensurePlan(studentPersonId, accountId, serviceRelationId, directorUserId, accountOpenedAt));
    }

    @PostMapping("/plan/{planId}/complete")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:complete')")
    public CommonResult<Boolean> complete(@PathVariable Long planId, @RequestParam Long accountId,
                                          @RequestParam(required = false) Long directorUserId,
                                          @RequestParam String stageCode, @RequestParam LocalDateTime completedAt) {
        throw new IllegalStateException("阶段必须通过提交表单完成");
    }
}
