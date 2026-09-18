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
    @Resource private cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryOverviewService overview;
    @Resource private cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryCycleService cycles;
    public record RestartRequest(@NotNull Long accountId,@NotNull Long planId,@NotNull Integer version) {}
    @GetMapping("/reminders") @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:query')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryCycleService.Reminder>> reminders() {
        return success(cycles.reminders(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/reminders/acknowledge") @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:query')")
    public CommonResult<Boolean> acknowledge(@RequestBody @jakarta.validation.constraints.Size(max=500) java.util.List<Long> ids) {
        cycles.acknowledge(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),ids); return success(true);
    }
    @PostMapping("/reposition") @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:reposition') && @ss.hasPermission('zsjos:positioning-card:edit')")
    public CommonResult<Long> reposition(@RequestBody @jakarta.validation.Valid RestartRequest req) {
        return success(cycles.reposition(req.accountId(),req.planId(),req.version(),cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
    }
    @Resource private StudentDeliveryPlanService planService;
    @Resource private StudentDeliverySubmissionService submissionService;
    @Resource private StudentDeliveryDeferService deferService;
    @Resource private StudentDeliveryPlanMapper planMapper;
    @Resource private StudentDeliveryStageMapper stageMapper;
    @Resource private StudentDeliveryConfigMapper configMapper;

    @GetMapping("/config")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery-config:query')")
    public CommonResult<StudentDeliveryConfigReqVO> getConfig() {
        var row=configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>()
                .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled,true).orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getVersion).last("LIMIT 1"));
        var value=new StudentDeliveryConfigReqVO();
        value.setS0Days(row==null?3:row.getS0Days()).setS1Days(row==null?7:row.getS1Days()).setS2Days(row==null?7:row.getS2Days())
                .setS3Days(row==null?7:row.getS3Days()).setS4Days(row==null?10:row.getS4Days()).setS5Days(row==null?14:row.getS5Days()).setS6Days(0);
        return success(value);
    }


    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    @PutMapping("/config")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery-config:update')")
    public CommonResult<Boolean> updateConfig(@RequestBody @Validated StudentDeliveryConfigReqVO req) { var row=configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>().eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled,true).orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getVersion).last("LIMIT 1")); if(row==null) row=new cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO().setVersion(1).setEnabled(true); else row.setVersion(row.getVersion()+1); row.setS0Days(req.getS0Days()).setS1Days(req.getS1Days()).setS2Days(req.getS2Days()).setS3Days(req.getS3Days()).setS4Days(req.getS4Days()).setS5Days(req.getS5Days()).setS6Days(req.getS6Days()); row.setId(null); configMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO>().eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled,true).set(cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryConfigDO::getEnabled,false)); configMapper.insert(row); return success(true); }

    @GetMapping("/plan")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:query')")
    public CommonResult<StudentDeliveryPlanRespVO> getPlan(@RequestParam Long accountId, @RequestParam(required=false) Long planId) {
        return success(overview.get(accountId, cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), planId));
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
    public CommonResult<StudentDeliveryPlanRespVO> ensurePlan(@RequestParam @NotNull Long accountId) {
        Long userId=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        overview.ensure(accountId,userId); return success(overview.get(accountId,userId,null));
    }

    @PostMapping("/plan/{planId}/complete")
    @PreAuthorize("@ss.hasPermission('zsjos:student-delivery:complete')")
    public CommonResult<Boolean> complete(@PathVariable Long planId, @RequestParam Long accountId,
                                          @RequestParam(required = false) Long directorUserId,
                                          @RequestParam String stageCode, @RequestParam LocalDateTime completedAt) {
        throw new IllegalStateException("阶段必须通过提交表单完成");
    }
}
