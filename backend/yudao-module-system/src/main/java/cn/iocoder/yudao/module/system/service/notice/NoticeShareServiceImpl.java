package cn.iocoder.yudao.module.system.service.notice;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.*;
import cn.iocoder.yudao.module.system.dal.mysql.notice.*;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
public class NoticeShareServiceImpl implements NoticeShareService {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private NoticeMapper noticeMapper;
    @Resource private NoticeShareMapper shareMapper;
    @Resource private NoticeAttachmentMapper attachmentMapper;
    @Resource private FileApi fileApi;
    @Resource private TenantService tenantService;
    @Value("${system.notice-share.public-base-url:${ZSJOS_PUBLIC_H5_BASE_URL:}}")
    private String publicBaseUrl;

    @Override public NoticeShareRespVO get(Long noticeId) {
        if (noticeMapper.selectById(noticeId) == null) throw exception(NOTICE_NOT_FOUND);
        return response(shareMapper.selectByNoticeId(noticeId));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public NoticeShareRespVO open(NoticeShareOpenReqVO request, Long userId) {
        String baseUrl = requireBaseUrl();
        NoticeDO notice = lockNotice(request.getNoticeId());
        if (!"PUBLISHED".equals(notice.getPublishStatus())) throw exception(NOTICE_NOT_PUBLISHED);
        NoticeShareDO share = shareMapper.selectByNoticeId(notice.getId());
        if (share != null && Boolean.TRUE.equals(share.getActive())) throw exception(NOTICE_SHARE_ALREADY_OPEN);
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.getAttachmentIds()));
        Map<Long, NoticeAttachmentDO> attachments = new HashMap<>();
        attachmentMapper.selectListByNoticeId(notice.getId()).forEach(a -> attachments.put(a.getInfraFileId(), a));
        for (Long id : ids) {
            if (!attachments.containsKey(id) || fileApi.getFileInfo(id) == null) throw exception(NOTICE_ATTACHMENT_INVALID);
        }
        if (share == null) {
            share = new NoticeShareDO();
            share.setNoticeId(notice.getId());
            share.setVersion(0L);
        }
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        share.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        share.setActive(true);
        share.setVersion(share.getVersion() + 1);
        share.setAttachmentIds(JSONUtil.toJsonStr(ids));
        share.setOpenedBy(userId); share.setOpenedAt(LocalDateTime.now());
        if (share.getId() == null) shareMapper.insert(share);
        else shareMapper.reopen(share);
        NoticeShareRespVO result = response(share);
        result.setUrl(baseUrl + "/notice/share#token=" + share.getToken());
        return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void close(NoticeShareCloseReqVO request, Long userId) {
        lockNotice(request.getNoticeId());
        NoticeShareDO share = shareMapper.selectByNoticeId(request.getNoticeId());
        if (share == null || !Boolean.TRUE.equals(share.getActive())
                || !Objects.equals(request.getVersion(), share.getVersion())) throw exception(NOTICE_SHARE_CONFLICT);
        shareMapper.closeActive(request.getNoticeId(), userId);
    }

    @Override public NoticeSharePublicRespVO publicNotice(String token) {
        return withPublicShare(token, share -> {
            NoticeDO notice = published(share);
            NoticeSharePublicRespVO result = new NoticeSharePublicRespVO();
            result.setTitle(notice.getTitle());
            // The generic XssCleaner removes video tags; the public projection uses its own strict Jsoup safelist.
            result.setContent(NoticeShareContent.clean(notice.getContent()));
            result.setPublishTime(notice.getPublishTime());
            Set<Long> selected = new HashSet<>(ids(share));
            result.setAttachments(attachmentMapper.selectListByNoticeId(notice.getId()).stream()
                    .filter(a -> selected.contains(a.getInfraFileId()))
                    .map(a -> new NoticeSharePublicRespVO.Attachment(a.getInfraFileId(), a.getFileName(),
                            a.getMimeType(), a.getFileSize())).toList());
            return result;
        });
    }

    @Override public String attachmentUrl(String token, Long attachmentId) {
        return withPublicShare(token, share -> {
            published(share);
            if (!ids(share).contains(attachmentId)) throw exception(NOTICE_SHARE_RESOURCE_UNAVAILABLE);
            boolean bound = attachmentMapper.selectListByNoticeId(share.getNoticeId()).stream()
                    .anyMatch(a -> Objects.equals(a.getInfraFileId(), attachmentId));
            if (!bound || fileApi.getFileInfo(attachmentId) == null) throw exception(NOTICE_SHARE_RESOURCE_UNAVAILABLE);
            String url = fileApi.presignGetUrl(attachmentId, 600);
            if (url == null || !NoticeShareContent.safeUrl(url)) throw exception(NOTICE_SHARE_RESOURCE_UNAVAILABLE);
            return url;
        });
    }

    private <T> T withPublicShare(String token, java.util.function.Function<NoticeShareDO, T> action) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw exception(NOTICE_SHARE_INVALID);
        // Only the opaque-token lookup crosses tenants. All later reads re-enable tenant filtering.
        NoticeShareDO located = inContext(null, true, () -> shareMapper.selectByToken(token));
        if (located == null || located.getTenantId() == null) throw exception(NOTICE_SHARE_INVALID);
        return inContext(located.getTenantId(), false, () -> {
            var tenant = tenantService.getTenant(located.getTenantId());
            if (tenant == null || !Integer.valueOf(0).equals(tenant.getStatus())
                    || (tenant.getExpireTime() != null && !tenant.getExpireTime().isAfter(LocalDateTime.now())))
                throw exception(NOTICE_SHARE_INVALID);
            NoticeShareDO current = shareMapper.selectById(located.getId());
            if (current == null || !Boolean.TRUE.equals(current.getActive())
                    || !Objects.equals(current.getToken(), token)) throw exception(NOTICE_SHARE_INVALID);
            return action.apply(current);
        });
    }

    private static <T> T inContext(Long tenantId, boolean ignore, Supplier<T> action) {
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenantId); TenantContextHolder.setIgnore(ignore);
            return action.get();
        } finally {
            TenantContextHolder.setTenantId(previousTenant); TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private NoticeDO published(NoticeShareDO share) {
        NoticeDO notice = noticeMapper.selectById(share.getNoticeId());
        if (notice == null || !"PUBLISHED".equals(notice.getPublishStatus())) throw exception(NOTICE_SHARE_INVALID);
        return notice;
    }
    private NoticeDO lockNotice(Long id) {
        NoticeDO notice = noticeMapper.selectByIdForUpdate(id);
        if (notice == null) throw exception(NOTICE_NOT_FOUND);
        return notice;
    }
    private List<Long> ids(NoticeShareDO share) { return JSONUtil.toList(share.getAttachmentIds(), Long.class); }
    private NoticeShareRespVO response(NoticeShareDO share) {
        NoticeShareRespVO result = new NoticeShareRespVO();
        result.setActive(share != null && Boolean.TRUE.equals(share.getActive()));
        result.setAttachmentIds(result.isActive() ? ids(share) : List.of());
        if (share != null) {
            result.setVersion(share.getVersion()); result.setOpenedAt(share.getOpenedAt());
            result.setClosedAt(result.isActive() ? null : share.getClosedAt());
            if (result.isActive()) {
                // Configuration loss must not prevent revoking an already active share.
                try { result.setUrl(requireBaseUrl() + "/notice/share#token=" + share.getToken()); }
                catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
                    if (!Objects.equals(e.getCode(), NOTICE_SHARE_CONFIG_INVALID.getCode())) throw e;
                }
            }
        }
        return result;
    }
    private String requireBaseUrl() {
        String value = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(value);
            if (!NoticeShareContent.safeUrl(value) || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw exception(NOTICE_SHARE_CONFIG_INVALID);
        } catch (IllegalArgumentException e) { throw exception(NOTICE_SHARE_CONFIG_INVALID); }
        return value;
    }
}
