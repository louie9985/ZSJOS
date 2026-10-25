package cn.iocoder.yudao.module.zsjos.service.zsjkqt;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.vo.ZsjkqtVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.ZsjkqtController;
import cn.iocoder.yudao.module.zsjos.dal.mysql.zsjkqt.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.zsjkqt.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.junit.jupiter.api.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ZsjkqtServiceTest {
    static JsonNode json(String s){return JsonUtils.getObjectMapper().readTree(s);}
    static Profile profile(){return new Profile(" employee-key "," 合成人员 "," 合成部门 ","2026-06-01",true);}
    @Test void upsert_personnel_profile_adds_new_employee(){var root=json("{}");ZsjkqtService.upsertProfile(root,profile());assertEquals("合成人员",root.path("employee-key").path("name").asText());assertEquals("2026-06-01",root.path("employee-key").path("entry_date").asText());assertTrue(root.path("employee-key").path("enabled").asBoolean());}
    @Test void upsert_personnel_profile_updates_existing_employee_and_preserves_other_data(){var root=json("{\"employee-key\":{\"custom\":\"保留\",\"name\":\"旧\"},\"other\":{\"name\":\"其他\"}}");ZsjkqtService.upsertProfile(root,profile());assertEquals("保留",root.path("employee-key").path("custom").asText());assertEquals("其他",root.path("other").path("name").asText());assertEquals("合成人员",root.path("employee-key").path("name").asText());}
    @Test void upsert_personnel_profile_rejects_non_object_root(){assertThrows(Exception.class,()->ZsjkqtService.upsertProfile(json("[]"),profile()));}
    @Test void configuration_loads_actual_retained_schedules_and_empty_profiles(){var service=new ZsjkqtService();var mapper=mock(ZsjkqtConfigMapper.class);ReflectionTestUtils.setField(service,"configs",mapper);var schedules=service.config("schedules").value();assertEquals("custom",schedules.path("departments").path("销售部").path("mode").asText());assertEquals("21:30",schedules.path("departments").path("销售部").path("afternoon").path("end").asText());assertTrue(service.config("profiles").value().isEmpty());assertDoesNotThrow(()->ZsjkqtService.validateSchedules(schedules));}
    @Test void invalid_and_overlapping_schedules_are_rejected(){assertThrows(Exception.class,()->ZsjkqtService.validateSchedules(json("{\"default\":{\"morning\":{\"start\":\"09:00\",\"end\":\"15:00\"},\"afternoon\":{\"start\":\"13:00\",\"end\":\"18:00\"}},\"departments\":{}}")));}
    @Test void single_access_permission_is_enforced_at_both_boundaries() throws Exception {assertEquals("@ss.hasPermission('zsjos:zsjkqt:access')",ZsjkqtController.class.getAnnotation(PreAuthorize.class).value());assertEquals("@ss.hasPermission('zsjos:zsjkqt:access')",ZsjkqtService.class.getAnnotation(PreAuthorize.class).value());for(String name:new String[]{"get","save","rename","delete","export"}){var method=java.util.Arrays.stream(ZsjkqtService.class.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();assertNotNull(method.getAnnotation(ZsjosPermission.class));}}
    @Test void all_authorized_users_share_the_same_tenant_draft_without_owner_bypass(){var p=new ZsjkqtObjectPermissionProvider();var drafts=mock(ZsjkqtDraftMapper.class);var permissions=mock(PermissionApi.class);ReflectionTestUtils.setField(p,"drafts",drafts);ReflectionTestUtils.setField(p,"permissions",permissions);when(drafts.selectById(5L)).thenReturn(new ZsjkqtDraftDO());when(permissions.hasAnyPermissions(8L,ZsjkqtErrors.ACCESS)).thenReturn(true);assertTrue(p.hasPermission(5L,"update",8L));assertTrue(p.hasPermission(5L,"delete",8L));assertFalse(p.hasPermission(5L,"read",9L));assertFalse(p.hasPermission(6L,"read",8L));assertFalse(p.hasPermission(5L,"unsupported",8L));assertThrows(Exception.class,()->p.check(5L,"read",9L));assertThrows(Exception.class,()->p.check(6L,"read",8L));}
    @Test void stale_save_and_delete_are_rejected_without_blind_overwrite(){var s=new ZsjkqtService();var drafts=mock(ZsjkqtDraftMapper.class);ReflectionTestUtils.setField(s,"drafts",drafts);var row=new ZsjkqtDraftDO();row.setId(1L);row.setClientId("draft");row.setVersion(3);when(drafts.selectById(1L)).thenReturn(row);var snapshot=json("{\"schemaVersion\":1,\"month\":{\"year\":2026,\"month\":6,\"employees\":[]}}");assertThrows(Exception.class,()->s.save(1L,new Save("draft","合成",2,snapshot)));assertThrows(Exception.class,()->s.delete(1L,2));}
    @Test void snapshot_validation_keeps_manual_overrides_and_rejects_invalid_dates(){var valid=json("{\"schemaVersion\":1,\"project\":{\"year\":2026,\"month\":6,\"employees\":[{\"days\":[{\"day\":1,\"finalMorning\":\"事假\"}],\"summary\":{\"actualAttendanceDays\":99},\"manualSummaryOverrides\":{\"actualAttendanceDays\":true}}]}}");assertDoesNotThrow(()->ZsjkqtService.validateSnapshot(valid));assertEquals(99,valid.path("project").path("employees").get(0).path("summary").path("actualAttendanceDays").asInt());assertThrows(Exception.class,()->ZsjkqtService.validateSnapshot(json("{\"schemaVersion\":1,\"month\":{\"year\":2026,\"month\":13,\"employees\":[]}}")));}

    @Test void create_replay_only_acknowledges_the_snapshot_that_was_actually_saved() {
        var service = new ZsjkqtService();
        var mapper = mock(ZsjkqtDraftMapper.class);
        ReflectionTestUtils.setField(service, "drafts", mapper);
        var snapshot = json("{\"schemaVersion\":1,\"month\":{\"year\":2026,\"month\":6,\"employees\":[]}}");
        var existing = new ZsjkqtDraftDO();
        existing.setId(3L); existing.setClientId("retry"); existing.setName("原稿");
        existing.setYear(2026); existing.setMonth(6); existing.setEmployeeCount(0); existing.setConfirmedCount(0); existing.setVersion(0);
        existing.setSnapshotJson(JsonUtils.toJsonString(snapshot));
        when(mapper.selectOne(ZsjkqtDraftDO::getClientId, "retry")).thenReturn(existing);
        // Lambda arguments are distinct instances; answer through the wrapper-independent overload matcher.
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq("retry"))).thenReturn(existing);
        assertEquals(3L, service.create(new Save("retry", "原稿", 0, snapshot)).id());
        var changed = (tools.jackson.databind.node.ObjectNode) snapshot.deepCopy(); changed.put("payrollDays", 25);
        assertThrows(Exception.class, () -> service.create(new Save("retry", "原稿", 0, changed)));
        verify(mapper, never()).insert(any(ZsjkqtDraftDO.class));
    }

    @Test void malformed_scalar_and_oversized_source_fields_are_rejected_before_persistence() {
        for (String value : new String[]{"\"2026\"", "2026.5", "true"}) {
            assertThrows(Exception.class, () -> ZsjkqtService.validateSnapshot(json("{\"schemaVersion\":1,\"month\":{\"year\":" + value + ",\"month\":6,\"employees\":[]}}")));
        }
        var snapshot = (tools.jackson.databind.node.ObjectNode) json("{\"schemaVersion\":1,\"step\":\"review\",\"month\":{\"year\":2026,\"month\":6,\"employees\":[]}}");
        ((tools.jackson.databind.node.ObjectNode) snapshot.path("month")).put("sourceFileName", "x".repeat(256));
        assertThrows(Exception.class, () -> ZsjkqtService.validateSnapshot(snapshot));
        snapshot.put("step", "unknown");
        assertThrows(Exception.class, () -> ZsjkqtService.validateSnapshot(snapshot));
    }

    @Test void missing_config_does_not_create_a_competing_initialization_path() {
        var service = new ZsjkqtService(); var mapper = mock(ZsjkqtConfigMapper.class);
        ReflectionTestUtils.setField(service, "configs", mapper);
        var schedules = service.config("schedules").value();
        assertThrows(Exception.class, () -> service.saveSchedules(new ConfigSave(0, schedules)));
        verify(mapper, never()).insert(any(ZsjkqtConfigDO.class));
        assertThrows(Exception.class, () -> service.profile(new Profile("x", "x", "x", "2026-02-30", true)));
    }

    @Test void creator_and_renamer_names_are_recorded_from_authenticated_operator_context() {
        var service = new ZsjkqtService(); var mapper = mock(ZsjkqtDraftMapper.class);
        ReflectionTestUtils.setField(service, "drafts", mapper);
        var user = new cn.iocoder.yudao.framework.security.core.LoginUser();
        user.setId(8L); user.setInfo(java.util.Map.of("nickname", "合成操作人"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
        when(mapper.insert(any(ZsjkqtDraftDO.class))).thenAnswer(invocation -> {
            ZsjkqtDraftDO row = invocation.getArgument(0); row.setId(9L); return 1;
        });
        when(mapper.update(any(ZsjkqtDraftDO.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        try {
            var snapshot = json("{\"schemaVersion\":1,\"month\":{\"year\":2026,\"month\":6,\"employees\":[]}}");
            var draft = service.create(new Save("created", "合成项目", 0, snapshot));
            assertEquals("合成操作人", draft.updaterName());
            service.rename(9L, new Rename("新名称", 0));
            var capture = org.mockito.ArgumentCaptor.forClass(ZsjkqtDraftDO.class);
            verify(mapper).update(capture.capture(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
            assertEquals("合成操作人", capture.getValue().getUpdaterName());
            assertEquals("新名称", capture.getValue().getName());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test void profile_update_uses_fresh_audit_fields_instead_of_the_loaded_operator() {
        var service = new ZsjkqtService(); var mapper = mock(ZsjkqtConfigMapper.class);
        ReflectionTestUtils.setField(service,"configs",mapper);
        var loaded = new ZsjkqtConfigDO(); loaded.setId(9L); loaded.setVersion(3); loaded.setValueJson("{}");
        loaded.setUpdater("old-operator"); loaded.setUpdateTime(java.time.LocalDateTime.of(2000,1,1,0,0));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(loaded);
        service.profile(profile());
        var capture = org.mockito.ArgumentCaptor.forClass(ZsjkqtConfigDO.class);
        verify(mapper).updateById(capture.capture());
        assertNull(capture.getValue().getUpdater());
        assertNull(capture.getValue().getUpdateTime());
        assertEquals(4,capture.getValue().getVersion());
        assertNotSame(loaded,capture.getValue());
    }
}
