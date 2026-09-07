package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@ExtendWith(MockitoExtension.class)
class LeadSupplementRemarkTest {
    @InjectMocks LeadSubmitterActionService service;
    @Mock LeadMapper leadMapper;
    @Mock LeadIntendedProductMapper productMapper;
    @Mock AreaApi areaApi;
    @Mock ZsjosProductSkuService productSkuService;
    @Mock BusinessEventMapper eventMapper;
    @Mock LeadSubmissionIdentityService identityService;
    @Mock AdminUserApi adminUserApi;
    @Mock PartnerMapper partnerMapper;
    final Map<String, BusinessEventDO> events = new HashMap<>();
    LeadDO lead;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        lead = new LeadDO(); lead.setId(1L); lead.setRemark("A"); lead.setStatus(STATUS_SUBMITTED);
        lead.setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER); lead.setProviderOwnerId(10L); lead.setLeadCategory("cat");
        lenient().when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        lenient().when(eventMapper.selectByIdempotencyKeyForUpdate(anyString())).thenAnswer(i -> events.get(i.getArgument(0)));
        lenient().doAnswer(i -> { BusinessEventDO event = i.getArgument(0); event.setId((long) events.size() + 1); events.put(event.getIdempotencyKey(), event); return 1; })
                .when(eventMapper).insert(any(BusinessEventDO.class));
        AreaRespDTO province = new AreaRespDTO(); province.setId(1); province.setType(2); province.setStatus(0);
        AreaRespDTO city = new AreaRespDTO(); city.setId(2); city.setParentId(1); city.setType(3); city.setStatus(0);
        lenient().when(areaApi.getArea(1)).thenReturn(province); lenient().when(areaApi.getArea(2)).thenReturn(city);
        lenient().when(productSkuService.validateLeadProduct(any(), anyBoolean(), any(), anyBoolean())).thenReturn(mock(LeadProductSnapshot.class));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void appendPreservesInitialAndRetriesAreIdempotent() {
        service.supplement(1L, 10L, request("one", " B "));
        service.supplement(1L, 10L, request("one", "B"));
        service.supplement(1L, 10L, request("two", "C"));
        assertEquals("A", lead.getRemark()); assertEquals(2, events.size());
        assertEquals("B", LeadRemarkHistoryService.appendedRemark(events.get("one")));
        assertThrows(RuntimeException.class, () -> service.supplement(1L, 10L, request("one", "changed")));
        verify(leadMapper, times(2)).updateById(lead);
    }
    @Test void blankStillUpdatesOtherFieldsWithoutErasingRemark() {
        service.supplement(1L, 10L, request("blank", " \n "));
        assertEquals("A", lead.getRemark()); assertEquals("2", lead.getCityCode());
        assertEquals("", LeadRemarkHistoryService.appendedRemark(events.get("blank")));
    }
    @Test void ownershipAndLifecycleRejectWithoutWrites() {
        assertThrows(RuntimeException.class, () -> service.supplement(1L, 20L, request("x", "B")));
        lead.setStatus(STATUS_INVALID);
        assertThrows(RuntimeException.class, () -> service.supplement(1L, 10L, request("x", "B")));
        verify(leadMapper, never()).updateById(any(LeadDO.class)); assertTrue(events.isEmpty());
    }
    @Test void partnerAppendRetainsTypedSubject() {
        lead.setProviderOwnerType(PROVIDER_OWNER_PARTNER); lead.setProviderOwnerId(70L);
        service.supplementForPartner(1L, 70L, request("partner", "B"));
        assertEquals(PROVIDER_OWNER_PARTNER, LeadRemarkHistoryService.payload(events.get("partner")).get("submitterType"));
        assertEquals("A", lead.getRemark());
    }

    @Test void remarkLengthValidationAndNormalizedDigest() {
        try (var validator = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertTrue(validator.getValidator().validate(request("x", "中".repeat(1000))).isEmpty());
            assertFalse(validator.getValidator().validate(request("x", "中".repeat(1001))).isEmpty());
        }
        assertEquals(LeadSubmitterActionService.supplementDigest(request("x", " B ")),
                LeadSubmitterActionService.supplementDigest(request("y", "B")));
    }

    @Test void wrongTenantDoesNotReadOrWriteAnotherLead() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> service.supplement(1L, 10L, request("x", "B")));
        verify(eventMapper, never()).insert(any(BusinessEventDO.class));
    }
    private static LeadSubmitterSupplementReqVO request(String key, String remark) {
        LeadProductReqVO product = new LeadProductReqVO(); product.setSpuRef("spu"); product.setPrimary(true);
        LeadSubmitterSupplementReqVO request = new LeadSubmitterSupplementReqVO(); request.setProvinceCode("1"); request.setCityCode("2");
        request.setLeadCategory("cat"); request.setIntendedProducts(List.of(product)); request.setRemark(remark); request.setIdempotencyKey(key); return request;
    }

    @Test void concurrentReplayAndDistinctCommandsCommitWithoutLostRemarks() throws Exception {
        var jdbc = database();
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Void> command = () -> {
                TenantContextHolder.setTenantId(1L);
                try { start.await(); tx.executeWithoutResult(s -> service.supplement(1L, 10L, request("concurrent", "B"))); }
                finally { TenantContextHolder.clear(); }
                return null;
            };
            var first = executor.submit(command); var second = executor.submit(command); start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS); second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM note_event", Integer.class));
            var b = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { tx.executeWithoutResult(s -> service.supplement(1L, 10L, request("b", "B"))); }
                finally { TenantContextHolder.clear(); }
            });
            var c = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { tx.executeWithoutResult(s -> service.supplement(1L, 10L, request("c", "C"))); }
                finally { TenantContextHolder.clear(); }
            });
            b.get(10, java.util.concurrent.TimeUnit.SECONDS); c.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM note_event", Integer.class));
            assertEquals("A", jdbc.queryForObject("SELECT remark FROM note_lead WHERE id=1", String.class));
        }
    }

    @Test void laterFailureRollsBackLeadAndEventInOneTransaction() {
        var jdbc = database();
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        doThrow(new IllegalStateException("controlled failure")).when(productMapper).deleteByLeadId(1L);
        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(s -> service.supplement(1L, 10L, request("rollback", "B"))));
        assertNull(jdbc.queryForObject("SELECT city FROM note_lead WHERE id=1", String.class));
        assertEquals("A", jdbc.queryForObject("SELECT remark FROM note_lead WHERE id=1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM note_event", Integer.class));
    }

    private org.springframework.jdbc.core.JdbcTemplate database() {
        // Isolated H2 persistence exercises the actual command inside real Spring transactions.
        // MySQL's production table shape/charset is verified separately using a temporary table.
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:remarks" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE note_lead(id BIGINT PRIMARY KEY, remark VARCHAR(1000), city VARCHAR(32))");
        jdbc.execute("CREATE TABLE note_event(event_key VARCHAR(128) PRIMARY KEY, lead_id BIGINT, payload VARCHAR(10000))");
        jdbc.update("INSERT INTO note_lead(id,remark) VALUES(1,'A')");
        doAnswer(i -> jdbc.queryForObject("SELECT * FROM note_lead WHERE id=? FOR UPDATE", (rs, n) -> {
            LeadDO row = new LeadDO(); row.setId(rs.getLong("id")); row.setRemark(rs.getString("remark"));
            row.setCityCode(rs.getString("city")); row.setStatus(STATUS_SUBMITTED); row.setLeadCategory("cat");
            row.setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER); row.setProviderOwnerId(10L); return row;
        }, new Object[]{i.getArgument(0)})).when(leadMapper).selectByIdForUpdate(1L, 1L);
        doAnswer(i -> {
            var rows = jdbc.query("SELECT * FROM note_event WHERE event_key=? FOR UPDATE", (rs, n) -> {
                BusinessEventDO event = new BusinessEventDO(); event.setEventType(LeadSupplementSnapshot.EVENT);
                event.setAggregateType("lead"); event.setAggregateId(rs.getLong("lead_id"));
                event.setRelatedObjectRefs(rs.getString("payload")); return event;
            }, new Object[]{i.getArgument(0)});
            return rows.isEmpty() ? null : rows.getFirst();
        }).when(eventMapper).selectByIdempotencyKeyForUpdate(anyString());
        doAnswer(i -> { LeadDO row = i.getArgument(0); return jdbc.update("UPDATE note_lead SET remark=?,city=? WHERE id=?",
                row.getRemark(), row.getCityCode(), row.getId()); }).when(leadMapper).updateById(any(LeadDO.class));
        lenient().doAnswer(i -> { BusinessEventDO row = i.getArgument(0); return jdbc.update("INSERT INTO note_event VALUES(?,?,?)",
                row.getIdempotencyKey(), row.getAggregateId(), row.getRelatedObjectRefs()); }).when(eventMapper).insert(any(BusinessEventDO.class));
        return jdbc;
    }
}
