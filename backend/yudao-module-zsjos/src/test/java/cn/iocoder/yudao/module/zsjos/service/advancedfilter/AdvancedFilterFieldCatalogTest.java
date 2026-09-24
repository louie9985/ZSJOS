package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedFilterFieldCatalogTest {
    private final AdvancedFilterService service = new AdvancedFilterService();

    @Test void authoritativeOptionSourcesDoNotFallBackToEmbeddedChoices() {
        var fields = AdvancedFilterFieldCatalog.fields();
        Map.of("lead.status", "zsjos_lead_status", "lead.assignmentStatus", "zsjos_lead_assignment_status",
                "appeal.status", "zsjos_lead_appeal_status").forEach((key, type) -> {
            var field = fields.get(key);
            assertEquals("dict:" + type, field.optionSource());
            assertTrue(field.options().isEmpty(), "Dictionary failure must not use embedded choices");
            assertEquals("dictionary", AdvancedFilterMetadata.describe(field).optionSourceType());
        });
        assertEquals(List.of("lead", "student"), fields.get("person.identityStatus").options().stream()
                .map(cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO.OptionVO::value).toList());
        assertEquals(List.of(cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.TYPE_VALID,
                cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.TYPE_DEAL),
                fields.get("cashback.type").options().stream().map(option -> option.value()).toList());
        var emptyUsers = service.catalog("lead", List.of()).fields().stream()
                .filter(field -> field.fieldKey().equals("lead.ownerUserId")).findFirst().orElseThrow();
        assertTrue(emptyUsers.options().isEmpty());
        assertNull(emptyUsers.optionSource(), "Empty scoped users must not trigger a client-wide lookup");
    }

    @Test void everyFieldAndDerivedDurationHasCompleteMetadata() {
        for (String scene : AdvancedFilterFieldCatalog.SCENES) {
            for (var field : service.catalog(scene).fields()) {
                assertTrue(field.supportedScenes().contains(scene), field.fieldKey());
                assertFalse(field.supportedPages().isEmpty(), field.fieldKey());
                assertEquals("inherit_query_authorization", field.permission());
                assertEquals("inherit_query_data_scope", field.dataScope());
                assertNotNull(field.optionSourceType());
                assertEquals(!"standard".equals(field.sensitivity()), field.sensitive());
                assertFalse(field.sortable());
                assertFalse(field.deprecated());
                if (!field.fieldKey().equals("duration.diff")) {
                    assertEquals(AdvancedFilterFieldCatalog.fields().get(field.fieldKey()).bindings().keySet(),
                            java.util.Set.copyOf(field.supportedScenes()));
                } else {
                    assertEquals(List.of(scene), field.supportedScenes());
                    assertEquals("catalog_dates", field.optionSourceType());
                }
            }
        }
    }

    @Test void optionResolutionPreservesMetadataAndAuthoritativeSources() {
        var raw = service.catalog("lead").fields().stream()
                .filter(f -> f.fieldKey().equals("lead.ownerUserId")).findFirst().orElseThrow();
        var resolved = service.catalog("lead", List.of()).fields().stream()
                .filter(f -> f.fieldKey().equals(raw.fieldKey())).findFirst().orElseThrow();
        assertEquals("unresolved", raw.optionsState());
        assertEquals("empty", resolved.optionsState());
        assertNull(resolved.optionSource());
        assertEquals("visible-users", resolved.declaredOptionSource());
        assertEquals("visible_users", resolved.optionSourceType());
        assertEquals(raw.supportedPages(), resolved.supportedPages());
        assertEquals(raw.sensitivity(), resolved.sensitivity());
        assertEquals(raw.permission(), resolved.permission());
        var department = service.catalog("lead").fields().stream()
                .filter(f -> f.fieldKey().equals("lead.ownerDeptId")).findFirst().orElseThrow();
        var filled = department.withResolvedOptions(List.of(
                new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO.OptionVO("1", "组织")));
        assertEquals("ready", filled.optionsState());
        assertEquals("visible-departments", filled.declaredOptionSource());
        assertEquals(department.supportedScenes(), filled.supportedScenes());
        assertEquals(department.dataScope(), filled.dataScope());
    }

    @Test void serializesAdditiveMetadataWithoutExposingSqlOrChangingLegacyPayload() {
        String json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(service.catalog("lead"));
        assertTrue(json.contains("\"fields\""));
        assertTrue(json.contains("\"relativeDateOptions\""));
        for (String key : List.of("fieldKey", "label", "group", "valueType", "operators", "options",
                "supportedScenes", "supportedPages", "permission", "dataScope", "sensitive", "sensitivity",
                "sortable", "deprecated", "optionSourceType", "declaredOptionSource")) {
            assertTrue(json.contains("\"" + key + "\""), key);
        }
        assertFalse(json.contains("SELECT 1"));
        assertFalse(json.contains("bindings"));
        assertFalse(json.contains("tenant_id"));
        var fields = service.catalog("lead").fields();
        assertEquals("dictionary", fields.stream().filter(f -> f.fieldKey().equals("lead.category"))
                .findFirst().orElseThrow().optionSourceType());
        assertTrue(fields.stream().filter(f -> f.fieldKey().equals("person.mobile"))
                .findFirst().orElseThrow().sensitive());
    }

    @Test void retainsCatalogCoverageForBothFrontendSceneContracts() {
        Map<String, Integer> expected = Map.of("lead", 80, "order", 72, "lead_appeal", 80,
                "duplicate_review", 18, "registration", 79, "student", 77,
                "subordinate_sales", 26, "cashback", 20, "withdrawal", 25);
        assertEquals(195, AdvancedFilterFieldCatalog.fields().size());
        expected.forEach((scene, count) -> {
            var fields = service.catalog(scene).fields();
            assertEquals(count, fields.size(), scene);
            assertEquals(fields.size(), fields.stream().map(f -> f.fieldKey()).distinct().count(), scene);
        });
    }

    @Test void sharesIdentityDefinitionsWithoutLeakingPageSpecificFields() {
        var registry = AdvancedFilterFieldCatalog.fields();
        assertTrue(registry.get("person.name").bindings().keySet().containsAll(
                List.of("lead", "order", "lead_appeal", "registration", "student")));
        var group = new AdvancedFilterGroupReqVO();
        group.setLogic("AND");
        var condition = new AdvancedFilterConditionReqVO();
        condition.setFieldKey("appeal.reason");
        condition.setOperator("contains");
        condition.setValue("复核");
        group.getConditions().add(condition);
        assertDoesNotThrow(() -> service.validate("lead_appeal", group));
        assertThrows(ServiceException.class, () -> service.validate("lead", group));
        assertFalse(service.catalog("order").fields().stream().anyMatch(f -> f.fieldKey().equals("appeal.reason")));
    }

    @Test void rejectsDuplicateKeysAndProtectsBindingsFromMutation() {
        var field = AdvancedFilterFieldCatalog.fields().get("person.name");
        var fields = new LinkedHashMap<String, AdvancedFilterFields.Field>();
        AdvancedFilterFields.add(fields, field);
        assertThrows(IllegalStateException.class, () -> AdvancedFilterFields.add(fields, field));
        assertSame(field, fields.get(field.key()));
        assertThrows(UnsupportedOperationException.class, () -> field.bindings().clear());
        assertThrows(UnsupportedOperationException.class, () -> AdvancedFilterFieldCatalog.fields().clear());
    }
}
