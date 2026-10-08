package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.*;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.mysql.notice.*;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Import({NoticeShareServiceImpl.class, NoticeServiceImpl.class})
class NoticeShareServiceImplTest extends BaseDbUnitTest {
    @Resource NoticeShareServiceImpl service;
    @Resource NoticeServiceImpl notices;
    @Resource NoticeMapper noticeMapper;
    @Resource NoticeShareMapper shares;
    @Resource NoticeAttachmentMapper attachments;
    @Resource NoticeReadMapper reads;
    @Resource MybatisPlusInterceptor interceptor;
    @MockitoBean FileApi files;
    @MockitoBean TenantService tenants;
    @MockitoBean XssCleaner cleaner;
    @MockitoBean DeptService depts;
    @MockitoBean AdminUserService users;
    @MockitoBean PermissionService permissions;
    @MockitoBean WebSocketSenderApi websocket;
    TenantLineInnerInterceptor tenantInterceptor;

    @BeforeEach void setup() {
        tenantInterceptor = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
        interceptor.addInnerInterceptor(tenantInterceptor);
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://h5.example.com");
        when(cleaner.clean(anyString())).thenAnswer(i -> i.getArgument(0));
        when(tenants.getTenant(anyLong())).thenAnswer(i -> TenantDO.builder().id(i.getArgument(0)).status(0)
                .expireTime(LocalDateTime.now().plusDays(1)).build());
        when(files.getFileInfo(anyLong())).thenAnswer(i -> {
            FileInfoRespDTO f = new FileInfoRespDTO(); f.setId(i.getArgument(0)); return f;
        });
    }
    @AfterEach void clear() {
        interceptor.setInterceptors(new ArrayList<>(interceptor.getInterceptors().stream().filter(i -> i != tenantInterceptor).toList()));
        TenantContextHolder.clear();
    }
    private NoticeDO notice(String state, String audience) {
        NoticeDO n = new NoticeDO(); n.setTitle("中世健公开文章"); n.setContent("<p>正文</p>");
        n.setType(1); n.setStatus(0); n.setPublishStatus(state); n.setAudienceType(audience);
        n.setPublishTime(LocalDateTime.now()); noticeMapper.insert(n); return n;
    }
    private NoticeShareOpenReqVO openRequest(Long id, Long... files) {
        NoticeShareOpenReqVO r = new NoticeShareOpenReqVO(); r.setNoticeId(id); r.setAttachmentIds(List.of(files)); return r;
    }
    private NoticeShareCloseReqVO closeRequest(Long id, long version) {
        NoticeShareCloseReqVO r = new NoticeShareCloseReqVO(); r.setNoticeId(id); r.setVersion(version); return r;
    }
    private String token(NoticeShareRespVO r) { return r.getUrl().split("token=")[1]; }
    private void attach(Long noticeId, Long fileId) {
        NoticeAttachmentDO a = new NoticeAttachmentDO(); a.setNoticeId(noticeId); a.setInfraFileId(fileId);
        a.setFileName("说明.pdf"); a.setFileSize(1024L); a.setMimeType("application/pdf"); a.setSort(0); attachments.insert(a);
    }
    @Test void lifecycleAndTargetNoticesDoNotWriteEmployeeReadRecords() {
        NoticeDO n = notice("PUBLISHED", "TARGET");
        assertFalse(service.get(n.getId()).isActive());
        var first = service.open(openRequest(n.getId()), 7L);
        assertEquals(43, token(first).length());
        assertTrue(service.publicNotice(token(first)).getAttachments().isEmpty());
        assertEquals(0L, reads.selectCount());
        assertServiceException(() -> service.open(openRequest(n.getId()), 7L), NOTICE_SHARE_ALREADY_OPEN);
        service.close(closeRequest(n.getId(), first.getVersion()), 7L);
        assertServiceException(() -> service.publicNotice(token(first)), NOTICE_SHARE_INVALID);
        var second = service.open(openRequest(n.getId()), 7L);
        assertNull(shares.selectByNoticeId(n.getId()).getClosedAt());
        assertNull(shares.selectByNoticeId(n.getId()).getClosedBy());
        assertNotEquals(token(first), token(second));
        assertServiceException(() -> service.close(closeRequest(n.getId(), first.getVersion()), 7L), NOTICE_SHARE_CONFLICT);
        assertServiceException(() -> service.publicNotice(token(first)), NOTICE_SHARE_INVALID);
        assertNotNull(service.publicNotice(token(second)));
        notices.offlineNotice(n.getId());
        assertFalse(shares.selectByNoticeId(n.getId()).getActive());
        assertServiceException(() -> service.publicNotice(token(second)), NOTICE_SHARE_INVALID);
        Long copy = notices.copyNotice(n.getId());
        assertFalse(service.get(copy).isActive());
    }
    @Test void rejectsDraftOfflineAndMissingConfigurationWithoutCreatingLinks() {
        for (String state : List.of("DRAFT", "OFFLINE")) {
            var n = notice(state, "ALL");
            assertServiceException(() -> service.open(openRequest(n.getId()), 7L), NOTICE_NOT_PUBLISHED);
        }
        var n = notice("PUBLISHED", "ALL");
        for (String url : List.of("", "https://user:secret@example.com", "/relative", "https://example.com?x=1")) {
            ReflectionTestUtils.setField(service, "publicBaseUrl", url);
            assertServiceException(() -> service.open(openRequest(n.getId()), 7L), NOTICE_SHARE_CONFIG_INVALID);
        }
        assertEquals(0L, shares.selectCount());
    }
    @Test void missingConfigurationNeverPreventsClosingExistingShare() {
        var n = notice("PUBLISHED", "ALL");
        var active = service.open(openRequest(n.getId()), 7L);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "");
        assertTrue(service.get(n.getId()).isActive());
        assertNull(service.get(n.getId()).getUrl());
        service.close(closeRequest(n.getId(), active.getVersion()), 7L);
        assertFalse(service.get(n.getId()).isActive());
    }
    @Test void onlySelectedBoundFilesCanBeResolvedAndMissingFilesRemainActionable() {
        var n = notice("PUBLISHED", "ALL"); attach(n.getId(), 101L); attach(n.getId(), 102L);
        var other = notice("PUBLISHED", "ALL"); attach(other.getId(), 103L);
        assertServiceException(() -> service.open(openRequest(n.getId(), 103L), 7L), NOTICE_ATTACHMENT_INVALID);
        var first = service.open(openRequest(n.getId(), 101L), 7L);
        assertEquals(List.of(101L), service.publicNotice(token(first)).getAttachments().stream().map(a -> a.id()).toList());
        assertServiceException(() -> service.attachmentUrl(token(first), 102L), NOTICE_SHARE_RESOURCE_UNAVAILABLE);
        when(files.presignGetUrl(101L, 600)).thenReturn("https://files.example.com/short-lived");
        assertEquals("https://files.example.com/short-lived", service.attachmentUrl(token(first), 101L));
        when(files.getFileInfo(101L)).thenReturn(null);
        assertServiceException(() -> service.attachmentUrl(token(first), 101L), NOTICE_SHARE_RESOURCE_UNAVAILABLE);
        service.close(closeRequest(n.getId(), first.getVersion()), 7L);
        assertServiceException(() -> service.attachmentUrl(token(first), 101L), NOTICE_SHARE_INVALID);
    }
    @Test void tokenRestoresItsTenantAndNeverTrustsRequestTenantOrLeaksContext() {
        var n = notice("PUBLISHED", "ALL"); attach(n.getId(), 101L);
        var first = service.open(openRequest(n.getId(), 101L), 7L);
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> service.get(n.getId()), NOTICE_NOT_FOUND);
        var other = notice("PUBLISHED", "ALL"); attach(other.getId(), 202L);
        assertServiceException(() -> service.open(openRequest(other.getId(), 101L), 8L), NOTICE_ATTACHMENT_INVALID);
        assertEquals(n.getTitle(), service.publicNotice(token(first)).getTitle());
        assertEquals(2L, TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
        when(tenants.getTenant(1L)).thenReturn(TenantDO.builder().id(1L).status(1).build());
        assertServiceException(() -> service.publicNotice(token(first)), NOTICE_SHARE_INVALID);
        assertEquals(2L, TenantContextHolder.getTenantId());
        TenantContextHolder.setIgnore(true);
        assertServiceException(() -> service.publicNotice("invalid"), NOTICE_SHARE_INVALID);
        assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void publicHtmlRemovesExecutableAndRelativeResources() {
        var n = notice("PUBLISHED", "ALL");
        n.setContent("<script>alert(1)</script><p onclick='bad()'>正文</p><iframe src='https://x.test'></iframe>"
                + "<a href='javascript:bad()'>链接</a><img src='/internal' onerror='bad()'>"
                + "<img src='https://files.example.com/a.png'><video src='https://files.example.com/a.mp4'></video>");
        noticeMapper.updateById(n);
        var html = service.publicNotice(token(service.open(openRequest(n.getId()), 7L))).getContent();
        assertFalse(html.contains("script")); assertFalse(html.contains("onclick")); assertFalse(html.contains("iframe"));
        assertFalse(html.contains("/internal")); assertTrue(html.contains("a.png")); assertTrue(html.contains("a.mp4"));
    }
    @Test void concurrentOpenProducesOneActiveLinkAndOfflineWinsFinalState() throws Exception {
        var n = notice("PUBLISHED", "ALL");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> open = () -> {
                TenantContextHolder.setTenantId(1L);
                try { start.await(); service.open(openRequest(n.getId()), 7L); return true; }
                catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
                    assertEquals(NOTICE_SHARE_ALREADY_OPEN.getCode(), e.getCode()); return false;
                } finally { TenantContextHolder.clear(); }
            };
            var a = pool.submit(open); var b = pool.submit(open); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1L, shares.selectCount());
        notices.offlineNotice(n.getId());
        assertFalse(service.get(n.getId()).isActive());
    }
    @Test void concurrentOpenCloseAndOfflineCannotLeaveAnActiveShare() throws Exception {
        var n = notice("PUBLISHED", "ALL");
        var first = service.open(openRequest(n.getId()), 7L);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int operation = 0; operation < 3; operation++) {
                final int command = operation;
                tasks.add(pool.submit(() -> {
                    TenantContextHolder.setTenantId(1L);
                    try {
                        start.await();
                        if (command == 0) service.open(openRequest(n.getId()), 7L);
                        else if (command == 1) service.close(closeRequest(n.getId(), first.getVersion()), 7L);
                        else notices.offlineNotice(n.getId());
                    } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
                        assertTrue(Set.of(NOTICE_SHARE_ALREADY_OPEN.getCode(), NOTICE_NOT_PUBLISHED.getCode(),
                                NOTICE_SHARE_CONFLICT.getCode()).contains(e.getCode()));
                    } catch (InterruptedException e) { throw new RuntimeException(e); }
                    finally { TenantContextHolder.clear(); }
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        }
        assertEquals("OFFLINE", noticeMapper.selectById(n.getId()).getPublishStatus());
        assertFalse(service.get(n.getId()).isActive());
        assertServiceException(() -> service.publicNotice(token(first)), NOTICE_SHARE_INVALID);
    }
}
