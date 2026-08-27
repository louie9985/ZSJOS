package cn.iocoder.yudao.module.system.service.notice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.*;
import cn.iocoder.yudao.module.system.dal.mysql.notice.*;
import cn.iocoder.yudao.module.system.enums.notice.NoticePublishStatusEnum;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
public class NoticeServiceImpl implements NoticeService {
    private static final int MAX_TITLE_LENGTH = 50;
    private static final String COPY_TITLE_SUFFIX = "（副本）";
    private static final long MAX_ATTACHMENT_SIZE = 20L * 1024 * 1024;
    private static final int DOWNLOAD_URL_TTL_SECONDS = 600;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip");

    @Resource private NoticeMapper noticeMapper;
    @Resource private NoticeAttachmentMapper attachmentMapper;
    @Resource private NoticeReadMapper readMapper;
    @Resource private FileApi fileApi;
    @Resource private XssCleaner xssCleaner;
    @Resource private WebSocketSenderApi webSocketSenderApi;

    @Override
    @Transactional
    public Long createNotice(NoticeSaveReqVO reqVO, Long userId) {
        NoticeDO notice = BeanUtils.toBean(reqVO, NoticeDO.class);
        notice.setContent(cleanContent(reqVO.getContent()));
        notice.setStatus(CommonStatusEnum.ENABLE.getStatus());
        notice.setPublishStatus(NoticePublishStatusEnum.DRAFT.getStatus());
        noticeMapper.insert(notice);
        replaceAttachments(notice.getId(), reqVO.getAttachments(), userId, Set.of());
        return notice.getId();
    }

    @Override
    @Transactional
    public void updateNotice(NoticeSaveReqVO reqVO, Long userId) {
        NoticeDO existing = requireNotice(reqVO.getId());
        requireDraft(existing);
        Set<Long> existingFileIds = attachmentMapper.selectListByNoticeId(existing.getId()).stream()
                .map(NoticeAttachmentDO::getInfraFileId).collect(Collectors.toSet());
        NoticeDO update = BeanUtils.toBean(reqVO, NoticeDO.class);
        update.setContent(cleanContent(reqVO.getContent()));
        update.setPublishStatus(null);
        update.setStatus(CommonStatusEnum.ENABLE.getStatus());
        noticeMapper.updateById(update);
        replaceAttachments(existing.getId(), reqVO.getAttachments(), userId, existingFileIds);
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        requireDraft(requireNotice(id));
        attachmentMapper.deleteByNoticeIds(List.of(id));
        readMapper.deleteByNoticeIds(List.of(id));
        noticeMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteNoticeList(List<Long> ids) {
        for (Long id : ids) requireDraft(requireNotice(id));
        attachmentMapper.deleteByNoticeIds(ids);
        readMapper.deleteByNoticeIds(ids);
        noticeMapper.deleteByIds(ids);
    }

    @Override
    public PageResult<NoticeRespVO> getNoticePage(NoticePageReqVO reqVO) {
        PageResult<NoticeDO> page = noticeMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toAdminResp).toList(), page.getTotal());
    }

    @Override
    public NoticeRespVO getNotice(Long id) {
        return toAdminResp(requireNotice(id));
    }

    @Override
    public NoticeAttachmentVO uploadAttachment(MultipartFile file, Long userId) throws Exception {
        validateUpload(file);
        FileInfoRespDTO saved = fileApi.createFileInfo(file.getBytes(), file.getOriginalFilename(),
                "system/notice/" + userId, file.getContentType());
        NoticeAttachmentVO result = toAttachmentVO(saved, 0);
        try {
            result.setDownloadUrl(fileApi.presignGetUrl(saved.getId(), DOWNLOAD_URL_TTL_SECONDS));
        } catch (RuntimeException ignored) {
            result.setDownloadUrl(null);
        }
        return result;
    }

    @Override
    @Transactional
    public void publishNotice(Long id) {
        requireDraft(requireNotice(id));
        NoticeDO update = new NoticeDO();
        update.setId(id);
        update.setPublishStatus(NoticePublishStatusEnum.PUBLISHED.getStatus());
        update.setPublishTime(LocalDateTime.now());
        update.setOfflineTime(null);
        noticeMapper.updateById(update);
        announceChange(id);
    }

    @Override
    @Transactional
    public void offlineNotice(Long id) {
        NoticeDO notice = requireNotice(id);
        if (!NoticePublishStatusEnum.PUBLISHED.getStatus().equals(notice.getPublishStatus())) {
            throw exception(NOTICE_NOT_PUBLISHED);
        }
        NoticeDO update = new NoticeDO();
        update.setId(id);
        update.setPublishStatus(NoticePublishStatusEnum.OFFLINE.getStatus());
        update.setOfflineTime(LocalDateTime.now());
        noticeMapper.updateById(update);
        announceChange(id);
    }

    @Override
    @Transactional
    public Long copyNotice(Long id) {
        NoticeDO source = requireNotice(id);
        NoticeDO copy = new NoticeDO();
        copy.setTitle(StrUtil.subWithLength(source.getTitle(), 0,
                MAX_TITLE_LENGTH - COPY_TITLE_SUFFIX.length()) + COPY_TITLE_SUFFIX);
        copy.setType(source.getType());
        copy.setContent(source.getContent());
        copy.setStatus(CommonStatusEnum.ENABLE.getStatus());
        copy.setPublishStatus(NoticePublishStatusEnum.DRAFT.getStatus());
        noticeMapper.insert(copy);
        List<NoticeAttachmentDO> attachments = attachmentMapper.selectListByNoticeId(id);
        for (int i = 0; i < attachments.size(); i++) {
            NoticeAttachmentDO target = BeanUtils.toBean(attachments.get(i), NoticeAttachmentDO.class);
            target.setId(null);
            target.setNoticeId(copy.getId());
            target.setSort(i);
            attachmentMapper.insert(target);
        }
        return copy.getId();
    }

    @Override
    public PageResult<NoticeMyRespVO> getMyNoticePage(PageParam reqVO, Long userId) {
        PageResult<NoticeDO> page = noticeMapper.selectPublishedPage(reqVO);
        Map<Long, NoticeReadDO> reads = readMapper.selectListByNoticeIdsAndUserId(
                page.getList().stream().map(NoticeDO::getId).toList(), userId).stream()
                .collect(Collectors.toMap(NoticeReadDO::getNoticeId, Function.identity()));
        return new PageResult<>(page.getList().stream().map(item -> toMyResp(item, reads.get(item.getId()), false)).toList(), page.getTotal());
    }

    @Override
    public NoticeMyRespVO getMyNotice(Long id, Long userId) {
        NoticeDO notice = requirePublishedNotice(id);
        return toMyResp(notice, readMapper.selectByNoticeIdAndUserId(id, userId), true);
    }

    @Override
    public NoticeUnreadSummaryRespVO getUnreadSummary(Long userId) {
        PageParam all = new PageParam();
        all.setPageNo(1);
        all.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<NoticeDO> published = noticeMapper.selectPublishedPage(all).getList();
        Set<Long> readIds = readMapper.selectListByNoticeIdsAndUserId(
                published.stream().map(NoticeDO::getId).toList(), userId).stream()
                .map(NoticeReadDO::getNoticeId).collect(Collectors.toSet());
        List<NoticeDO> unread = published.stream().filter(item -> !readIds.contains(item.getId())).toList();
        NoticeUnreadSummaryRespVO result = new NoticeUnreadSummaryRespVO();
        result.setUnreadCount((long) unread.size());
        result.setLatest(unread.isEmpty() ? null : toMyResp(unread.get(0), null, false));
        return result;
    }

    @Override
    public void markRead(Long id, Long userId) {
        requirePublishedNotice(id);
        if (readMapper.selectByNoticeIdAndUserId(id, userId) != null) return;
        NoticeReadDO read = new NoticeReadDO();
        read.setNoticeId(id);
        read.setUserId(userId);
        read.setReadTime(LocalDateTime.now());
        try {
            readMapper.insert(read);
        } catch (DuplicateKeyException ignored) {
            // The tenant-notice-user unique key makes concurrent read acknowledgements idempotent.
        }
    }

    private NoticeDO requireNotice(Long id) {
        NoticeDO notice = id == null ? null : noticeMapper.selectById(id);
        if (notice == null) throw exception(NOTICE_NOT_FOUND);
        return notice;
    }

    private NoticeDO requirePublishedNotice(Long id) {
        NoticeDO notice = requireNotice(id);
        if (!NoticePublishStatusEnum.PUBLISHED.getStatus().equals(notice.getPublishStatus())) {
            throw exception(NOTICE_NOT_PUBLISHED);
        }
        return notice;
    }

    private void requireDraft(NoticeDO notice) {
        if (!NoticePublishStatusEnum.DRAFT.getStatus().equals(notice.getPublishStatus())) {
            throw exception(NOTICE_NOT_DRAFT);
        }
    }

    private String cleanContent(String content) {
        String cleaned = xssCleaner.clean(StrUtil.nullToEmpty(content));
        if (StrUtil.isBlank(Jsoup.parse(cleaned).text()) && !cleaned.contains("<img")) {
            throw exception(NOTICE_CONTENT_EMPTY);
        }
        return cleaned;
    }

    private void validateUpload(MultipartFile file) {
        String extension = StrUtil.subAfter(StrUtil.nullToEmpty(file.getOriginalFilename()), '.', true).toLowerCase();
        if (file.isEmpty() || file.getSize() > MAX_ATTACHMENT_SIZE || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw exception(NOTICE_ATTACHMENT_INVALID);
        }
    }

    private void replaceAttachments(Long noticeId, List<NoticeAttachmentVO> requested, Long userId, Set<Long> existingFileIds) {
        List<NoticeAttachmentVO> items = requested == null ? List.of() : requested;
        if (items.size() > 10 || items.stream().map(NoticeAttachmentVO::getInfraFileId).distinct().count() != items.size()) {
            throw exception(NOTICE_ATTACHMENT_INVALID);
        }
        attachmentMapper.deleteByNoticeIds(List.of(noticeId));
        for (int i = 0; i < items.size(); i++) {
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(items.get(i).getInfraFileId());
            } catch (RuntimeException ex) {
                throw exception(NOTICE_ATTACHMENT_INVALID);
            }
            boolean ownedUpload = Objects.equals(String.valueOf(userId), file.getCreator())
                    && StrUtil.startWith(file.getPath(), "system/notice/" + userId + "/");
            if ((!ownedUpload && !existingFileIds.contains(file.getId())) || file.getSize() == null || file.getSize() > MAX_ATTACHMENT_SIZE) {
                throw exception(NOTICE_ATTACHMENT_INVALID);
            }
            NoticeAttachmentDO row = new NoticeAttachmentDO();
            row.setNoticeId(noticeId);
            row.setInfraFileId(file.getId());
            row.setFileName(file.getName());
            row.setMimeType(file.getType());
            row.setFileSize(file.getSize());
            row.setSort(i);
            attachmentMapper.insert(row);
        }
    }

    private NoticeRespVO toAdminResp(NoticeDO notice) {
        NoticeRespVO result = BeanUtils.toBean(notice, NoticeRespVO.class);
        result.setAttachments(attachmentMapper.selectListByNoticeId(notice.getId()).stream().map(this::toAttachmentVO).toList());
        return result;
    }

    private NoticeMyRespVO toMyResp(NoticeDO notice, NoticeReadDO read, boolean withContent) {
        NoticeMyRespVO result = BeanUtils.toBean(notice, NoticeMyRespVO.class);
        if (!withContent) result.setContent(null);
        result.setRead(read != null);
        result.setReadTime(read == null ? null : read.getReadTime());
        result.setAttachments(withContent
                ? attachmentMapper.selectListByNoticeId(notice.getId()).stream().map(this::toAttachmentVO).toList()
                : List.of());
        return result;
    }

    private NoticeAttachmentVO toAttachmentVO(NoticeAttachmentDO row) {
        NoticeAttachmentVO result = BeanUtils.toBean(row, NoticeAttachmentVO.class);
        try {
            result.setDownloadUrl(fileApi.presignGetUrl(row.getInfraFileId(), DOWNLOAD_URL_TTL_SECONDS));
        } catch (RuntimeException ignored) {
            // Keep the attachment snapshot visible when the Infra file is no longer available.
            result.setDownloadUrl(null);
        }
        return result;
    }

    private NoticeAttachmentVO toAttachmentVO(FileInfoRespDTO file, int sort) {
        NoticeAttachmentVO result = new NoticeAttachmentVO();
        result.setInfraFileId(file.getId());
        result.setFileName(file.getName());
        result.setMimeType(file.getType());
        result.setFileSize(file.getSize());
        result.setSort(sort);
        return result;
    }

    private void announceChange(Long id) {
        Runnable task = () -> webSocketSenderApi.sendObject(UserTypeEnum.ADMIN.getValue(),
                "notice-published", Map.of("noticeId", id));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { task.run(); }
        });
    }
}
