package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTalkRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTalkSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.registration.MyStudentService;
import cn.iocoder.yudao.module.zsjos.service.registration.MediaStudentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/media-students")
public class MediaStudentController {
    @Resource private MyStudentService studentService;
    @Resource private MediaStudentService mediaStudentService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:media-student:query-my')")
    public CommonResult<PageResult<MyStudentRespVO>> page(@Valid MyStudentPageReqVO req) {
        return success(studentService.getMediaPage(SecurityFrameworkUtils.getLoginUserId(), req));
    }

    @GetMapping("/{personId}")
    @PreAuthorize("@ss.hasPermission('zsjos:media-student:query-my')")
    public CommonResult<MediaStudentDetailRespVO> get(@PathVariable Long personId) {
        return success(mediaStudentService.getDetail(SecurityFrameworkUtils.getLoginUserId(), personId));
    }

    @GetMapping("/target")
    @PreAuthorize("@ss.hasPermission('zsjos:media-student:query-my')")
    public CommonResult<MediaStudentTargetRespVO> target(@RequestParam String bizType, @RequestParam Long bizId) {
        return success(mediaStudentService.resolveTarget(SecurityFrameworkUtils.getLoginUserId(), bizType, bizId));
    }

    @GetMapping("/{personId}/talk-records")
    @PreAuthorize("@ss.hasPermission('zsjos:media-student:query-my')")
    public CommonResult<List<MediaStudentTalkRespVO>> talkRecords(@PathVariable Long personId) {
        return success(mediaStudentService.getTalkRecords(SecurityFrameworkUtils.getLoginUserId(), personId));
    }

    @PostMapping("/{personId}/talk-records")
    @PreAuthorize("@ss.hasPermission('zsjos:media-student:query-my')")
    public CommonResult<Long> createTalkRecord(@PathVariable Long personId,
                                                @Valid @RequestBody MediaStudentTalkSaveReqVO request) {
        return success(mediaStudentService.createTalkRecord(SecurityFrameworkUtils.getLoginUserId(), personId, request));
    }
}
