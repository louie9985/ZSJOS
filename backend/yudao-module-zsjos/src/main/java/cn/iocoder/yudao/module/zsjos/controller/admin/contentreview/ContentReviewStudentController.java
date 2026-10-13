package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewStudentService;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountProfileService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import cn.iocoder.yudao.module.zsjos.service.positioninginterview.PositioningInterviewService;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioninginterview.vo.PositioningInterviewVO;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/content-review/batch/{batchId}/student-read")
@PreAuthorize("@ss.hasPermission('zsjos:media-student:query-my') && @ss.hasPermission('zsjos:content-review:query')")
public class ContentReviewStudentController {
    @Resource private ContentReviewStudentService service;
    @Resource private MediaAccountProfileService profiles;
    @Resource private PositioningCardService positioning;
    @Resource private PositioningInterviewService interviews;
    @GetMapping("/accounts/{accountId}/positioning-overview")
    public CommonResult<PositioningCardService.ReviewOverview> positioningOverview(@PathVariable Long batchId,@PathVariable Long accountId) {
        return success(positioning.overviewForReview(batchId,accountId,getLoginUserId()));
    }
    @GetMapping("/accounts/{accountId}/file-content")
    public org.springframework.http.ResponseEntity<byte[]> fileContent(@PathVariable Long batchId,@PathVariable Long accountId,
            @RequestParam String kind,@RequestParam(required=false) Long submissionId,@RequestParam Long fileId) throws Exception {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(service.fileContent(batchId,accountId,kind,submissionId,fileId,getLoginUserId()));
    }
    @GetMapping public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentRespVO> student(@PathVariable Long batchId, @RequestParam Long personId) {
        return success(service.student(batchId,personId,getLoginUserId()));
    }
    @GetMapping("/accounts/{accountId}/profile") public CommonResult<MediaAccountProfileVO> profile(@PathVariable Long batchId,@PathVariable Long accountId) {
        return success(profiles.getForReview(batchId,accountId,getLoginUserId()));
    }
    @GetMapping("/accounts/{accountId}/positioning") public CommonResult<List<PositioningCardRespVO>> positioning(@PathVariable Long batchId,@PathVariable Long accountId) {
        return success(positioning.accountForReview(batchId,accountId,getLoginUserId()));
    }
    @GetMapping("/services/{relationId}/interview") public CommonResult<PositioningInterviewVO.Context> interview(@PathVariable Long batchId,@PathVariable Long relationId) {
        return success(interviews.contextForReview(batchId,relationId,getLoginUserId()));
    }
    @GetMapping("/accounts/{accountId}/file") public CommonResult<PositioningCardService.CardFile> file(@PathVariable Long batchId,@PathVariable Long accountId,
            @RequestParam String kind,@RequestParam(required=false) Long submissionId,@RequestParam Long fileId) {
        return success(service.file(batchId,accountId,kind,submissionId,fileId,getLoginUserId()));
    }
}
