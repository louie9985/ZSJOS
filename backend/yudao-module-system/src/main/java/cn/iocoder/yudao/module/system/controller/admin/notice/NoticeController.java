package cn.iocoder.yudao.module.system.controller.admin.notice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.service.notice.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 通知公告")
@RestController
@RequestMapping("/system/notice")
@Validated
public class NoticeController {
    @Resource private NoticeService noticeService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('system:notice:create')")
    public CommonResult<Long> createNotice(@Valid @RequestBody NoticeSaveReqVO reqVO) {
        return success(noticeService.createNotice(reqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('system:notice:update')")
    public CommonResult<Boolean> updateNotice(@Valid @RequestBody NoticeSaveReqVO reqVO) {
        noticeService.updateNotice(reqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('system:notice:delete')")
    public CommonResult<Boolean> deleteNotice(@RequestParam("id") Long id) {
        noticeService.deleteNotice(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @PreAuthorize("@ss.hasPermission('system:notice:delete')")
    public CommonResult<Boolean> deleteNoticeList(@RequestParam("ids") List<Long> ids) {
        noticeService.deleteNoticeList(ids);
        return success(true);
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:notice:query')")
    public CommonResult<PageResult<NoticeRespVO>> getNoticePage(@Validated NoticePageReqVO reqVO) {
        return success(noticeService.getNoticePage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:notice:query')")
    public CommonResult<NoticeRespVO> getNotice(@RequestParam("id") Long id) {
        return success(noticeService.getNotice(id));
    }

    @GetMapping("/recipient-options")
    @Operation(summary = "获得公告接收人选项")
    @PreAuthorize("@ss.hasAnyPermissions('system:notice:create','system:notice:update')")
    public CommonResult<NoticeRecipientOptionsRespVO> getRecipientOptions() {
        return success(noticeService.getRecipientOptions());
    }

    @PostMapping("/attachment/upload")
    @Operation(summary = "上传公告附件")
    @PreAuthorize("@ss.hasAnyPermissions('system:notice:create','system:notice:update')")
    public CommonResult<NoticeAttachmentVO> uploadAttachment(@RequestParam("file") MultipartFile file) throws Exception {
        return success(noticeService.uploadAttachment(file, getLoginUserId()));
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('system:notice:publish')")
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        noticeService.publishNotice(id);
        return success(true);
    }

    @PostMapping("/offline")
    @PreAuthorize("@ss.hasPermission('system:notice:offline')")
    public CommonResult<Boolean> offline(@RequestParam("id") Long id) {
        noticeService.offlineNotice(id);
        return success(true);
    }

    @PostMapping("/copy")
    @PreAuthorize("@ss.hasPermission('system:notice:create')")
    public CommonResult<Long> copy(@RequestParam("id") Long id) {
        return success(noticeService.copyNotice(id));
    }

    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('system:notice:read')")
    public CommonResult<PageResult<NoticeMyRespVO>> getMyNoticePage(@Valid NoticeMyPageReqVO reqVO) {
        return success(noticeService.getMyNoticePage(reqVO, getLoginUserId()));
    }

    @GetMapping("/my-cursor")
    @Operation(summary = "使用游标获得我的通知公告")
    @PreAuthorize("@ss.hasPermission('system:notice:read')")
    public CommonResult<CursorPageResult<NoticeMyRespVO>> getMyNoticeCursor(@Validated NoticeMyCursorReqVO reqVO) {
        return success(noticeService.getMyNoticeCursor(reqVO, getLoginUserId()));
    }

    @GetMapping("/my-get")
    @PreAuthorize("@ss.hasPermission('system:notice:read')")
    public CommonResult<NoticeMyRespVO> getMyNotice(@RequestParam("id") Long id) {
        return success(noticeService.getMyNotice(id, getLoginUserId()));
    }

    @GetMapping("/unread-summary")
    @PreAuthorize("@ss.hasPermission('system:notice:read')")
    public CommonResult<NoticeUnreadSummaryRespVO> getUnreadSummary() {
        return success(noticeService.getUnreadSummary(getLoginUserId()));
    }

    @PutMapping("/mark-read")
    @PreAuthorize("@ss.hasPermission('system:notice:read')")
    public CommonResult<Boolean> markRead(@RequestParam("id") Long id) {
        noticeService.markRead(id, getLoginUserId());
        return success(true);
    }
}
