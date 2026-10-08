package cn.iocoder.yudao.module.system.service.notice;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.*;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.notice.*;
import cn.iocoder.yudao.module.system.enums.notice.NoticePublishStatusEnum;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
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
    private static final long MAX_ATTACHMENT_SIZE = 100L * 1024 * 1024;
    private static final int DOWNLOAD_URL_TTL_SECONDS = 600;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip");

    @Resource private NoticeMapper noticeMapper;
    @Resource private NoticeShareMapper noticeShareMapper;
    @Resource private NoticeAttachmentMapper attachmentMapper;
    @Resource private NoticeReadMapper readMapper;
    @Resource private NoticeReadStatisticsMapper statisticsMapper;
    @Resource private FileApi fileApi;
    @Resource private XssCleaner xssCleaner;
    @Resource private WebSocketSenderApi webSocketSenderApi;
    @Resource private NoticeRecipientMapper recipientMapper;
    @Resource private DeptService deptService;
    @Resource private AdminUserService userService;
    @Resource private PermissionService permissionService;

    @Override
    @Transactional
    public Long createNotice(NoticeSaveReqVO reqVO, Long userId) {
        NoticeDO notice = BeanUtils.toBean(reqVO, NoticeDO.class);
        applyAudience(notice, reqVO);
        applySource(notice, reqVO.getSourceDeptId(), userId);
        notice.setAudienceSummary(describeAudience(notice));
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
        NoticeDO existing = lockNotice(reqVO.getId());
        requireDraft(existing);
        Set<Long> existingFileIds = attachmentMapper.selectListByNoticeId(existing.getId()).stream()
                .map(NoticeAttachmentDO::getInfraFileId).collect(Collectors.toSet());
        NoticeDO update = BeanUtils.toBean(reqVO, NoticeDO.class);
        applyAudience(update, reqVO);
        applySource(update, reqVO.getSourceDeptId() == null ? existing.getSourceDeptId() : reqVO.getSourceDeptId(), userId);
        update.setAudienceSummary(describeAudience(update));
        update.setContent(cleanContent(reqVO.getContent()));
        update.setPublishStatus(null);
        update.setStatus(CommonStatusEnum.ENABLE.getStatus());
        noticeMapper.updateById(update);
        replaceAttachments(existing.getId(), reqVO.getAttachments(), userId, existingFileIds);
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        requireDraft(lockNotice(id));
        attachmentMapper.deleteByNoticeIds(List.of(id));
        readMapper.deleteByNoticeIds(List.of(id));
        recipientMapper.deleteByNoticeIds(List.of(id));
        noticeMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteNoticeList(List<Long> ids) {
        List<Long> orderedIds = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        for (Long id : orderedIds) requireDraft(lockNotice(id));
        attachmentMapper.deleteByNoticeIds(orderedIds);
        readMapper.deleteByNoticeIds(orderedIds);
        recipientMapper.deleteByNoticeIds(orderedIds);
        noticeMapper.deleteByIds(orderedIds);
    }

    @Override
    public PageResult<NoticeRespVO> getNoticePage(NoticePageReqVO reqVO) {
        PageResult<NoticeDO> page = noticeMapper.selectPage(reqVO);
        Map<Long, Long> recipientCounts = recipientMapper.selectCountMapByNoticeIds(page.getList().stream()
                .filter(notice -> "TARGET".equals(notice.getAudienceType()))
                .map(NoticeDO::getId).toList());
        return new PageResult<>(page.getList().stream()
                .map(notice -> toAdminResp(notice, recipientCounts.getOrDefault(notice.getId(), 0L)))
                .toList(), page.getTotal());
    }

    @Override
    public NoticeRespVO getNotice(Long id) {
        return toAdminResp(requireNotice(id));
    }

    @Override
    public NoticeRecipientOptionsRespVO getRecipientOptions() {
        List<DeptDO> depts = deptService.getDeptList(
                new DeptListReqVO().setStatus(CommonStatusEnum.ENABLE.getStatus()));
        List<AdminUserDO> users =
                userService.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus());
        Set<Long> permitted = permissionService.getEnabledUserIdsByPermission("system:notice:read");
        NoticeRecipientOptionsRespVO result = new NoticeRecipientOptionsRespVO();
        Long actorId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        AdminUserDO actor = actorId == null ? null : userService.getUser(actorId);
        if (actor != null && depts.stream().anyMatch(dept -> Objects.equals(dept.getId(), actor.getDeptId()))) {
            result.setDefaultSourceDeptId(actor.getDeptId());
        }
        result.setDepartments(depts.stream().map(dept -> {
            NoticeRecipientDeptVO vo = new NoticeRecipientDeptVO();
            vo.setId(dept.getId()); vo.setParentId(dept.getParentId()); vo.setName(dept.getName()); return vo;
        }).toList());
        result.setUsers(users.stream().map(user -> {
            NoticeRecipientUserVO vo = new NoticeRecipientUserVO();
            vo.setId(user.getId()); vo.setNickname(user.getNickname()); vo.setDeptId(user.getDeptId());
            vo.setSelectable(permitted.contains(user.getId()));
            vo.setDisabledReason(vo.isSelectable() ? null : "未获得公告阅读权限");
            return vo;
        }).toList());
        return result;
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
    public void publishNotice(Long id, Long userId) {
        NoticeDO notice = lockNotice(id);
        requireDraft(notice);
        freezeRecipients(notice);
        NoticeDO update = new NoticeDO();
        update.setId(id);
        userService.validateUserList(List.of(userId));
        AdminUserDO publisher = userService.getUser(userId);
        if (publisher == null) throw exception(USER_NOT_EXISTS);
        applySource(update, notice.getSourceDeptId(), userId);
        if (update.getSourceDeptId() == null) throw exception(DEPT_NOT_FOUND);
        update.setPublisherId(userId);
        update.setPublisherName(publisher.getNickname());
        update.setAudienceSummary(describeAudience(notice));
        update.setPublishStatus(NoticePublishStatusEnum.PUBLISHED.getStatus());
        update.setRecipientSnapshotComplete(true);
        update.setPublishTime(LocalDateTime.now());
        update.setOfflineTime(null);
        noticeMapper.updateById(update);
        announceChange(id);
    }

    @Override
    @Transactional
    public void offlineNotice(Long id) {
        NoticeDO notice = lockNotice(id);
        if (!NoticePublishStatusEnum.PUBLISHED.getStatus().equals(notice.getPublishStatus())) {
            throw exception(NOTICE_NOT_PUBLISHED);
        }
        NoticeDO update = new NoticeDO();
        update.setId(id);
        update.setPublishStatus(NoticePublishStatusEnum.OFFLINE.getStatus());
        update.setOfflineTime(LocalDateTime.now());
        noticeMapper.updateById(update);
        noticeShareMapper.closeActive(id, cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId());
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
        copy.setSourceDeptId(source.getSourceDeptId());
        copy.setSourceDeptName(source.getSourceDeptName());
        copy.setAudienceSummary(source.getAudienceSummary());
        copy.setContent(source.getContent());
        copy.setAudienceType(source.getAudienceType());
        copy.setTargetDeptIds(source.getTargetDeptIds());
        copy.setTargetUserIds(source.getTargetUserIds());
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
    public PageResult<NoticeMyRespVO> getMyNoticePage(NoticeMyPageReqVO reqVO, Long userId) {
        PageResult<NoticeDO> page = noticeMapper.selectPublishedPage(reqVO, userId);
        Map<Long, NoticeReadDO> reads = readMapper.selectListByNoticeIdsAndUserId(
                page.getList().stream().map(NoticeDO::getId).toList(), userId).stream()
                .collect(Collectors.toMap(NoticeReadDO::getNoticeId, Function.identity()));
        return new PageResult<>(page.getList().stream().map(item -> toMyResp(item, reads.get(item.getId()), false)).toList(), page.getTotal());
    }

    @Override
    public CursorPageResult<NoticeMyRespVO> getMyNoticeCursor(NoticeMyCursorReqVO reqVO, Long userId) {
        String filterHash = cursorFilterHash(reqVO);
        NoticeCursor cursor = decodeCursor(reqVO.getCursor(), userId, filterHash);
        LocalDateTime snapshotTime = cursor == null ? LocalDateTime.now() : cursor.snapshotTime();
        int limit = reqVO.getLimit() == null ? 20 : reqVO.getLimit();
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid notice cursor limit");
        List<NoticeDO> rows = noticeMapper.selectPublishedCursor(userId, snapshotTime,
                cursor == null ? null : cursor.highlighted(),
                cursor == null ? null : cursor.publishTime(), cursor == null ? null : cursor.id(), limit + 1,
                reqVO.getKeyword(), reqVO.getType(), reqVO.getHighlighted(), reqVO.getReadStatus(), reqVO.getPublishTime());
        boolean hasMore = rows.size() > limit;
        List<NoticeDO> list = hasMore ? rows.subList(0, limit) : rows;
        Map<Long, NoticeReadDO> reads = readMapper.selectListByNoticeIdsAndUserId(
                list.stream().map(NoticeDO::getId).toList(), userId).stream()
                .collect(Collectors.toMap(NoticeReadDO::getNoticeId, Function.identity()));
        String nextCursor = hasMore && !list.isEmpty()
                ? encodeCursor(list.get(list.size() - 1), userId, snapshotTime, filterHash) : null;
        return new CursorPageResult<>(list.stream().map(item -> toMyResp(item, reads.get(item.getId()), false)).toList(), nextCursor, hasMore);
    }

    private String encodeCursor(NoticeDO notice, Long userId, LocalDateTime snapshotTime, String filterHash) {
        String value = "v1|" + snapshotTime + "|" + (isHighlighted(notice, snapshotTime) ? "1" : "0")
                + "|" + notice.getPublishTime() + "|" + notice.getId() + "|" + userId + "|" + filterHash;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private NoticeCursor decodeCursor(String value, Long userId, String filterHash) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 7 || !"v1".equals(parts[0]) || !Objects.equals(Long.valueOf(parts[5]), userId)
                    || !Objects.equals(parts[6], filterHash)) {
                throw new IllegalArgumentException();
            }
            return new NoticeCursor(LocalDateTime.parse(parts[1]), "1".equals(parts[2]),
                    LocalDateTime.parse(parts[3]), Long.valueOf(parts[4]));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid notice cursor", ex);
        }
    }

    private String cursorFilterHash(NoticeMyCursorReqVO reqVO) {
        String publishTime = reqVO.getPublishTime() == null ? "" : Arrays.toString(reqVO.getPublishTime());
        String canonical = String.join("|", StrUtil.nullToEmpty(reqVO.getKeyword()),
                Objects.toString(reqVO.getType(), ""), Objects.toString(reqVO.getReadStatus(), ""),
                Objects.toString(reqVO.getHighlighted(), ""), publishTime);
        return DigestUtil.sha256Hex(canonical);
    }

    private record NoticeCursor(LocalDateTime snapshotTime, boolean highlighted, LocalDateTime publishTime, Long id) {}

    @Override
    public NoticeMyRespVO getMyNotice(Long id, Long userId) {
        NoticeDO notice = requirePublishedNotice(id);
        ensureRecipient(notice, userId);
        return toMyResp(notice, readMapper.selectByNoticeIdAndUserId(id, userId), true);
    }

    @Override
    public NoticeUnreadSummaryRespVO getUnreadSummary(Long userId) {
        NoticeMyPageReqVO all = new NoticeMyPageReqVO();
        all.setPageNo(1);
        all.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<NoticeDO> published = noticeMapper.selectPublishedPage(all, userId).getList();
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
        NoticeDO notice = requirePublishedNotice(id);
        ensureRecipient(notice, userId);
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

    private boolean hasCompleteRoster(NoticeDO notice) {
        return Boolean.TRUE.equals(notice.getRecipientSnapshotComplete())
                || ("TARGET".equals(notice.getAudienceType()) && recipientMapper.selectCountByNoticeId(notice.getId()) > 0);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public NoticeReadSummaryRespVO getReadSummary(Long id) {
        NoticeDO notice = requireNotice(id);
        NoticeReadSummaryRespVO result = new NoticeReadSummaryRespVO();
        boolean published = !NoticePublishStatusEnum.DRAFT.getStatus().equals(notice.getPublishStatus());
        result.setPublished(published);
        result.setRosterComplete(published && hasCompleteRoster(notice));
        if (!published) return result;
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        NoticeReadPageReqVO req = new NoticeReadPageReqVO();
        req.setId(id);
        req.setScope("ACTUAL");
        result.setActualReadCount(statisticsMapper.count(tenantId, req));
        if (result.getRosterComplete()) {
            req.setScope("EXPECTED");
            long expected = statisticsMapper.count(tenantId, req);
            result.setExpectedCount(expected);
            result.setDepartments(statisticsMapper.departments(tenantId, req));
            req.setScope("READ");
            long read = statisticsMapper.count(tenantId, req);
            result.setReadCount(read);
            result.setUnreadCount(expected - read);
            result.setReadRate(expected == 0 ? null : (double) read / expected);
            req.setScope("EXTRA");
            result.setExtraReadCount(statisticsMapper.count(tenantId, req));
            result.setExtraDepartments(statisticsMapper.departments(tenantId, req));
        } else {
            result.setDepartments(statisticsMapper.departments(tenantId, req));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<NoticeReadPersonRespVO> getReadPage(NoticeReadPageReqVO reqVO) {
        NoticeDO notice = requireNotice(reqVO.getId());
        if (NoticePublishStatusEnum.DRAFT.getStatus().equals(notice.getPublishStatus())) return new PageResult<>(List.of(), 0L);
        // Missing historical ALL rosters must never be reported as a known empty roster.
        if (!hasCompleteRoster(notice)) reqVO.setScope("ACTUAL");
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        long total = statisticsMapper.count(tenantId, reqVO);
        return new PageResult<>(total == 0 ? List.of() : statisticsMapper.page(tenantId, reqVO,
                (long) (reqVO.getPageNo() - 1) * reqVO.getPageSize()), total);
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

    private NoticeDO lockNotice(Long id) {
        NoticeDO notice = id == null ? null : noticeMapper.selectByIdForUpdate(id);
        if (notice == null) throw exception(NOTICE_NOT_FOUND);
        return notice;
    }

    private void applySource(NoticeDO notice, Long deptId, Long userId) {
        if (deptId == null && userId != null) {
            AdminUserDO actor = userService.getUser(userId);
            deptId = actor == null ? null : actor.getDeptId();
        }
        if (deptId == null) return; // Legacy drafts may lack a source; publication requires one.
        deptService.validateDeptList(List.of(deptId));
        DeptDO dept = deptService.getDept(deptId);
        if (dept == null) throw exception(DEPT_NOT_FOUND);
        notice.setSourceDeptId(deptId);
        notice.setSourceDeptName(dept.getName());
    }

    private String describeAudience(NoticeDO notice) {
        if (!"TARGET".equals(notice.getAudienceType())) return "全体员工";
        List<Long> ids = parseIds(notice.getTargetDeptIds());
        Map<Long, DeptDO> departments = ids.isEmpty() ? Map.of() : deptService.getDeptMap(ids);
        List<String> parts = new ArrayList<>();
        for (Long id : ids) {
            DeptDO dept = departments.get(id);
            parts.add(dept == null ? "部门已不可用" : dept.getName() + "（含子部门）");
        }
        int users = parseIds(notice.getTargetUserIds()).size();
        if (users > 0) parts.add("指定用户 " + users + " 人");
        return String.join("、", parts);
    }

    private void applyAudience(NoticeDO notice, NoticeSaveReqVO req) {
        String type = StrUtil.blankToDefault(req.getAudienceType(), "ALL").toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "TARGET").contains(type)) throw exception(NOTICE_RECIPIENT_INVALID);
        List<Long> deptIds = distinctIds(req.getTargetDeptIds());
        List<Long> userIds = distinctIds(req.getTargetUserIds());
        if ("ALL".equals(type)) {
            notice.setAudienceType(type);
            notice.setTargetDeptIds(JSONUtil.toJsonStr(List.of()));
            notice.setTargetUserIds(JSONUtil.toJsonStr(List.of()));
            return;
        }
        if (deptIds.isEmpty() && userIds.isEmpty()) throw exception(NOTICE_RECIPIENT_INVALID);
        if (!deptIds.isEmpty()) deptService.validateDeptList(deptIds);
        if (!userIds.isEmpty()) userService.validateUserList(userIds);
        if (!permissionService.getEnabledUserIdsByPermission("system:notice:read").containsAll(userIds)) {
            throw exception(NOTICE_RECIPIENT_INVALID);
        }
        notice.setAudienceType(type);
        notice.setTargetDeptIds(JSONUtil.toJsonStr(deptIds));
        notice.setTargetUserIds(JSONUtil.toJsonStr(userIds));
    }

    private List<Long> distinctIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void freezeRecipients(NoticeDO notice) {
        if (recipientMapper.selectCountByNoticeId(notice.getId()) > 0) {
            throw exception(NOTICE_RECIPIENT_INVALID);
        }
        Set<Long> deptIds = new LinkedHashSet<>(parseIds(notice.getTargetDeptIds()));
        if (!deptIds.isEmpty()) deptIds.addAll(deptService.getChildDeptList(deptIds).stream().map(item -> item.getId()).toList());
        Set<Long> userIds = new LinkedHashSet<>(parseIds(notice.getTargetUserIds()));
        if (!deptIds.isEmpty()) userIds.addAll(userService.getUserListByDeptIds(deptIds).stream().map(item -> item.getId()).toList());
        userIds.retainAll(permissionService.getEnabledUserIdsByPermission("system:notice:read"));
        if (!"TARGET".equals(notice.getAudienceType())) {
            userIds = new LinkedHashSet<>(permissionService.getEnabledUserIdsByPermission("system:notice:read"));
        }
        List<AdminUserDO> users = userIds.isEmpty() ? List.of() : userService.getUserList(userIds).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())).toList();
        if ("TARGET".equals(notice.getAudienceType()) && users.isEmpty()) throw exception(NOTICE_RECIPIENT_INVALID);
        Set<Long> profileDeptIds = users.stream().map(AdminUserDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeptDO> departments = profileDeptIds.isEmpty() ? Map.of() : deptService.getDeptMap(profileDeptIds);
        List<NoticeRecipientDO> recipients = users.stream().map(user -> {
            NoticeRecipientDO recipient = new NoticeRecipientDO();
            recipient.setNoticeId(notice.getId());
            recipient.setUserId(user.getId());
            recipient.setUserNameSnapshot(user.getNickname());
            recipient.setDeptIdSnapshot(user.getDeptId());
            DeptDO dept = user.getDeptId() == null ? null : departments.get(user.getDeptId());
            recipient.setDeptNameSnapshot(dept == null ? null : dept.getName());
            recipient.setProfileSnapshotComplete(true);
            return recipient;
        }).toList();
        if (!recipients.isEmpty()) recipientMapper.insertBatch(recipients, 500);
    }

    private List<Long> parseIds(String json) {
        if (StrUtil.isBlank(json)) return List.of();
        try {
            return JSONUtil.parseArray(json).toList(Long.class);
        } catch (RuntimeException ex) {
            throw exception(NOTICE_RECIPIENT_INVALID);
        }
    }

    private void ensureRecipient(NoticeDO notice, Long userId) {
        if (!"TARGET".equals(notice.getAudienceType())) return;
        if (!recipientMapper.existsByNoticeIdAndUserId(notice.getId(), userId)) {
            throw exception(NOTICE_RECIPIENT_INVALID);
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
        Map<Long, NoticeAttachmentDO> storedByFileId = attachmentMapper.selectListIncludingDeletedByNoticeId(noticeId)
                .stream().collect(Collectors.toMap(NoticeAttachmentDO::getInfraFileId, Function.identity()));
        List<NoticeAttachmentDO> validated = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(items.get(i).getInfraFileId());
            } catch (RuntimeException ex) {
                throw exception(NOTICE_ATTACHMENT_INVALID);
            }
            if (file == null) throw exception(NOTICE_ATTACHMENT_INVALID);
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
            validated.add(row);
        }
        attachmentMapper.deleteByNoticeIdExceptFileIds(noticeId,
                validated.stream().map(NoticeAttachmentDO::getInfraFileId).toList());
        for (NoticeAttachmentDO row : validated) {
            NoticeAttachmentDO stored = storedByFileId.get(row.getInfraFileId());
            if (stored == null) {
                attachmentMapper.insert(row);
            } else {
                // Reuse the binding: logical deletion does not release uk_notice_file.
                // Deleted rows do not expand existingFileIds or bypass upload ownership checks.
                if (Boolean.TRUE.equals(stored.getDeleted()) && attachmentMapper.restoreDeleted(stored) != 1) {
                    throw exception(NOTICE_ATTACHMENT_INVALID);
                }
                row.setId(stored.getId());
                attachmentMapper.updateById(row);
            }
        }
    }

    private NoticeRespVO toAdminResp(NoticeDO notice) {
        long recipientCount = "TARGET".equals(notice.getAudienceType())
                ? recipientMapper.selectCountByNoticeId(notice.getId()) : 0L;
        return toAdminResp(notice, recipientCount);
    }

    private NoticeRespVO toAdminResp(NoticeDO notice, long recipientCount) {
        NoticeRespVO result = BeanUtils.toBean(notice, NoticeRespVO.class);
        result.setHighlighted(isHighlighted(notice));
        result.setAttachments(attachmentMapper.selectListByNoticeId(notice.getId()).stream().map(this::toAttachmentVO).toList());
        result.setTargetDeptIds(parseIds(notice.getTargetDeptIds()));
        result.setTargetUserIds(parseIds(notice.getTargetUserIds()));
        result.setRecipientCount("TARGET".equals(notice.getAudienceType()) ? Math.toIntExact(recipientCount) : null);
        return result;
    }

    private NoticeMyRespVO toMyResp(NoticeDO notice, NoticeReadDO read, boolean withContent) {
        NoticeMyRespVO result = BeanUtils.toBean(notice, NoticeMyRespVO.class);
        result.setHighlighted(isHighlighted(notice));
        if (!withContent) result.setContent(null);
        result.setRead(read != null);
        result.setReadTime(read == null ? null : read.getReadTime());
        result.setAttachments(withContent
                ? attachmentMapper.selectListByNoticeId(notice.getId()).stream().map(this::toAttachmentVO).toList()
                : List.of());
        return result;
    }

    private boolean isHighlighted(NoticeDO notice) {
        return isHighlighted(notice, LocalDateTime.now());
    }

    private boolean isHighlighted(NoticeDO notice, LocalDateTime referenceTime) {
        return notice.getHighlightUntil() != null && notice.getHighlightUntil().isAfter(referenceTime);
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
