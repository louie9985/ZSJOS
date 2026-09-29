package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.*;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.notice.*;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import(NoticeServiceImpl.class)
class NoticeReadStatisticsTest extends BaseDbUnitTest {
    @Resource private NoticeServiceImpl service;
    @Resource private NoticeMapper notices;
    @Resource private NoticeRecipientMapper recipients;
    @Resource private NoticeReadMapper reads;
    @Resource private NoticeReadStatisticsMapper statistics;
    @Resource private javax.sql.DataSource dataSource;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean private FileApi files;
    @MockitoBean private XssCleaner cleaner;
    @MockitoBean private WebSocketSenderApi socket;
    @MockitoBean private DeptService departments;
    @MockitoBean private AdminUserService users;
    @MockitoBean private PermissionService permissions;
    @BeforeEach void tenant() {
        TenantContextHolder.setTenantId(0L);
        jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void stableRosterWithExtraReadersAndPaging() {
        NoticeDO notice = notice("ALL", true, "PUBLISHED");
        for (long i=1; i<=100; i++) recipient(notice.getId(), i, 0L);
        for (long i=1; i<=80; i++) read(notice.getId(), i, 0L);
        for (long i=101; i<=105; i++) read(notice.getId(), i, 0L);
        NoticeReadSummaryRespVO summary = service.getReadSummary(notice.getId());
        assertEquals(100L, summary.getExpectedCount());
        assertEquals(80L, summary.getReadCount());
        assertEquals(20L, summary.getUnreadCount());
        assertEquals(0.8, summary.getReadRate());
        assertEquals(5L, summary.getExtraReadCount());
        assertEquals(85L, summary.getActualReadCount());
        NoticeReadPageReqVO query = query(notice.getId(), "UNREAD");
        query.setPageSize(7); query.setPageNo(2);
        var page = service.getReadPage(query);
        assertEquals(20L, page.getTotal());
        assertEquals(7, page.getList().size());
        assertEquals(88L, page.getList().getFirst().getUserId());
        assertTrue(page.getList().getFirst().getAccountDeleted());
        assertEquals("发布用户88", page.getList().getFirst().getUserName());
        query.setName("发布用户99"); query.setPageNo(1); query.setDeptId(10L);
        assertEquals(1L, service.getReadPage(query).getTotal());
        assertEquals("发布部门", summary.getDepartments().getFirst().getName());
        read(notice.getId(), 90L, 2L);
        recipient(notice.getId(), 106L, 2L);
        assertEquals(20L, service.getReadSummary(notice.getId()).getUnreadCount());
        assertEquals(1L, statistics.count(2L, query(notice.getId(), "EXPECTED")));
    }

    @Test void legacyUnknownAndKnownEmptyAreDifferent() {
        NoticeDO legacy = notice("ALL", false, "OFFLINE");
        read(legacy.getId(), 1L, 0L);
        var unknown = service.getReadSummary(legacy.getId());
        assertFalse(unknown.getRosterComplete());
        assertNull(unknown.getExpectedCount()); assertNull(unknown.getUnreadCount());
        assertEquals(1L, unknown.getActualReadCount());
        assertEquals(1L, service.getReadPage(query(legacy.getId(), "UNREAD")).getTotal());
        var empty = service.getReadSummary(notice("ALL", true, "PUBLISHED").getId());
        assertTrue(empty.getRosterComplete()); assertEquals(0L, empty.getExpectedCount()); assertNull(empty.getReadRate());
        var draft = service.getReadSummary(notice("ALL", false, "DRAFT").getId());
        assertFalse(draft.getPublished()); assertNull(draft.getExpectedCount());
        NoticeDO targeted = notice("TARGET", false, "OFFLINE");
        NoticeRecipientDO old = recipient(targeted.getId(), 5L, 0L);
        old.setProfileSnapshotComplete(false); recipients.updateById(old);
        assertTrue(service.getReadSummary(targeted.getId()).getRosterComplete());
        var person = service.getReadPage(query(targeted.getId(), "EXPECTED")).getList().getFirst();
        assertEquals("CURRENT", person.getProfileSource()); assertNull(person.getUserName());
    }

    @Test void publicationFreezesProfilesAndCopyDoesNotInheritRoster() {
        AdminUserDO user = new AdminUserDO(); user.setId(7L); user.setNickname("发布姓名"); user.setDeptId(10L); user.setStatus(0);
        DeptDO dept = new DeptDO(); dept.setId(10L); dept.setName("原部门");
        when(permissions.getEnabledUserIdsByPermission("system:notice:read")).thenReturn(Set.of(7L));
        when(users.getUserList(Set.of(7L))).thenReturn(List.of(user));
        when(departments.getDeptMap(Set.of(10L))).thenReturn(Map.of(10L, dept));
        NoticeDO draft = notice("ALL", false, "DRAFT");
        service.publishNotice(draft.getId());
        assertTrue(notices.selectById(draft.getId()).getRecipientSnapshotComplete());
        user.setNickname("新姓名"); dept.setName("新部门");
        assertEquals("发布姓名", recipients.selectListByNoticeId(draft.getId()).getFirst().getUserNameSnapshot());
        assertEquals("原部门", recipients.selectListByNoticeId(draft.getId()).getFirst().getDeptNameSnapshot());
        service.markRead(draft.getId(), 8L);
        LocalDateTime first = reads.selectByNoticeIdAndUserId(draft.getId(), 8L).getReadTime();
        service.markRead(draft.getId(), 8L);
        assertEquals(first, reads.selectByNoticeIdAndUserId(draft.getId(), 8L).getReadTime());
        assertEquals(1L, service.getReadSummary(draft.getId()).getExtraReadCount());
        Long copy = service.copyNotice(draft.getId());
        assertFalse(Boolean.TRUE.equals(notices.selectById(copy).getRecipientSnapshotComplete()));
        assertEquals(0L, recipients.selectCountByNoticeId(copy));
    }

    @Test void currentProfileChangesNeverReplaceSnapshotOrShrinkRoster() {
        NoticeDO notice = notice("TARGET", true, "PUBLISHED");
        recipient(notice.getId(), 7L, 0L);
        jdbc.update("INSERT INTO system_users(id,username,nickname,dept_id,status,tenant_id) VALUES (7,'notice-fixture','新姓名',20,1,0)");
        jdbc.update("INSERT INTO system_dept(id,name,status,tenant_id) VALUES (20,'新部门',0,0)");
        var q = query(notice.getId(), "EXPECTED");
        var row = service.getReadPage(q).getList().getFirst();
        assertEquals("发布用户7", row.getUserName()); assertEquals(10L, row.getDeptId());
        assertEquals(1, row.getAccountStatus()); assertFalse(row.getAccountDeleted());
        q.setDeptId(20L); assertEquals(0L, service.getReadPage(q).getTotal());
        q.setDeptId(10L); assertEquals(1L, service.getReadPage(q).getTotal());
        jdbc.update("UPDATE system_users SET deleted=1 WHERE id=7");
        assertTrue(service.getReadPage(q).getList().getFirst().getAccountDeleted());
        assertEquals(1L, service.getReadSummary(notice.getId()).getUnreadCount());
        NoticeDO legacy = notice("TARGET", false, "OFFLINE");
        var old = recipient(legacy.getId(), 7L, 0L); old.setProfileSnapshotComplete(false); recipients.updateById(old);
        jdbc.update("UPDATE system_users SET deleted=0 WHERE id=7");
        var current = service.getReadPage(query(legacy.getId(), "EXPECTED")).getList().getFirst();
        assertEquals("新姓名", current.getUserName()); assertEquals("新部门", current.getDeptName());
        assertEquals("CURRENT", current.getProfileSource());
    }

    @Test void zeroRecipientPublicationCompletesAndResolutionFailureLeavesDraft() {
        NoticeDO empty = notice("ALL", false, "DRAFT");
        when(permissions.getEnabledUserIdsByPermission("system:notice:read")).thenReturn(Set.of());
        service.publishNotice(empty.getId());
        var result = service.getReadSummary(empty.getId());
        assertTrue(result.getRosterComplete()); assertEquals(0L, result.getExpectedCount()); assertNull(result.getReadRate());
        NoticeDO failed = notice("ALL", false, "DRAFT");
        when(permissions.getEnabledUserIdsByPermission("system:notice:read")).thenReturn(Set.of(7L));
        when(users.getUserList(Set.of(7L))).thenThrow(new IllegalStateException("profile resolution unavailable"));
        assertThrows(IllegalStateException.class, () -> service.publishNotice(failed.getId()));
        assertEquals("DRAFT", notices.selectById(failed.getId()).getPublishStatus());
        assertFalse(notices.selectById(failed.getId()).getRecipientSnapshotComplete());
        assertEquals(0L, recipients.selectCountByNoticeId(failed.getId()));
    }

    private NoticeDO notice(String audience, boolean complete, String status) {
        NoticeDO n = new NoticeDO(); n.setTitle("统计测试"); n.setContent("<p>正文</p>"); n.setType(2); n.setStatus(0);
        n.setAudienceType(audience); n.setPublishStatus(status); n.setRecipientSnapshotComplete(complete); notices.insert(n); return n;
    }
    private NoticeRecipientDO recipient(Long id, Long user, Long tenant) {
        NoticeRecipientDO r = new NoticeRecipientDO(); r.setNoticeId(id); r.setUserId(user); r.setTenantId(tenant);
        r.setUserNameSnapshot("发布用户" + user); r.setDeptIdSnapshot(10L); r.setDeptNameSnapshot("发布部门"); r.setProfileSnapshotComplete(true); recipients.insert(r); return r;
    }
    private void read(Long id, Long user, Long tenant) {
        NoticeReadDO r = new NoticeReadDO(); r.setNoticeId(id); r.setUserId(user); r.setTenantId(tenant); r.setReadTime(LocalDateTime.now()); reads.insert(r);
    }
    private NoticeReadPageReqVO query(Long id, String scope) {
        NoticeReadPageReqVO q = new NoticeReadPageReqVO(); q.setId(id); q.setScope(scope); return q;
    }
}
