package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Requirements come from the reviewed manifest; operator cases enumerate the actual public catalog. */
class AdvancedFilterCompletenessTest {
    private final AdvancedFilterService service = new AdvancedFilterService();
    private static final String PROBE = "contract_probe_' OR 1=1 --";

    AdvancedFilterCompletenessTest() {
        var organizations = mock(LeadFilterOrganizationService.class);
        when(organizations.ownerIds(any())).thenReturn(List.of(7L));
        ReflectionTestUtils.setField(service, "leadFilterOrganizations", organizations);
    }

    static JsonNode contract() throws Exception {
        try (var input = AdvancedFilterCompletenessTest.class.getResourceAsStream("/advanced-filter-contract.json")) {
            assertNotNull(input);
            return JsonUtils.getObjectMapper().readTree(input);
        }
    }

    static Path repository() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("frontend/workbench/package.json"))) path = path.getParent();
        return Objects.requireNonNull(path, "Run inside the repository");
    }

    @Test void everyPageHasCatalogAndRequiredListDetailExportFields() throws Exception {
        var manifest = contract();
        var scenes = new HashSet<String>();
        manifest.get("scenes").properties().forEach(entry -> scenes.add(entry.getKey()));
        assertEquals(AdvancedFilterFieldCatalog.SCENES, scenes, "Register new scenes in the independent coverage manifest");
        var pages = new HashSet<String>();
        for (var page : manifest.get("pages")) {
            String scene = page.get("scene").asText(), key = page.get("pageKey").asText();
            assertTrue(pages.add(key), key);
            var fields = service.catalog(scene).fields();
            assertFalse(fields.isEmpty(), key);
            assertTrue(fields.stream().allMatch(field -> field.supportedPages().contains(key)), key);
            var keys = fields.stream().map(AdvancedFilterCatalogRespVO.FieldVO::fieldKey).toList();
            for (String surface : List.of("list", "detail", "export")) {
                var required = manifest.get("scenes").get(scene).get(surface);
                if (required.isEmpty()) {
                    assertEquals("export", surface);
                    assertFalse(manifest.get("scenes").get(scene).get("exportNotApplicable").asText().isBlank());
                }
                for (var field : required) assertTrue(keys.contains(field.asText()), key + "/" + surface + ": " + field.asText());
            }
        }
        assertEquals(new HashSet<>(AdvancedFilterMetadata.pages(scenes)), pages, "Page metadata and requirements must evolve together");
    }

    @Test void everySelectHasAnAuditedAuthorityAndNoDuplicateValues() throws Exception {
        var owners = contract().get("embeddedOptionAuthorities");
        var embedded = new HashSet<String>();
        for (var field : AdvancedFilterFieldCatalog.fields().values()) {
            if (!field.type().equals("select")) continue;
            if (field.optionSource() != null) {
                assertTrue(field.optionSource().matches("dict:[a-zA-Z0-9_]+|visible-users|visible-departments|product-catalog:(spu|sku)"), field.key());
                assertTrue(field.options().isEmpty(), "External source must not have static fallback: " + field.key());
            } else {
                embedded.add(field.key());
                assertTrue(owners.has(field.key()), "Declare the business/technical owner of: " + field.key());
                assertTrue(Files.isRegularFile(repository().resolve(owners.get(field.key()).asText())), field.key());
                assertFalse(field.options().isEmpty(), field.key());
            }
            assertEquals(field.options().size(), field.options().stream().map(AdvancedFilterCatalogRespVO.OptionVO::value).distinct().count(), field.key());
        }
        var expected = new HashSet<String>();
        owners.properties().forEach(entry -> expected.add(entry.getKey()));
        assertEquals(expected, embedded, "Remove stale source declarations too");
    }

    @Test void publicTypesAndOperatorsStayWithinTheSharedConsumerContract() throws Exception {
        var contract = contract();
        for (String scene : AdvancedFilterFieldCatalog.SCENES) for (var field : service.catalog(scene).fields()) {
            for (var entry : Map.of("valueType", field.valueType(), "optionSourceType", field.optionSourceType(),
                    "sensitivity", field.sensitivity()).entrySet()) {
                var allowed = new HashSet<String>();
                contract.get("wireTypes").get(entry.getKey()).forEach(value -> allowed.add(value.asText()));
                assertTrue(allowed.contains(entry.getValue()), field.fieldKey() + ": " + entry);
            }
            var operators = new HashSet<String>();
            contract.get("operatorsByType").get(field.valueType()).forEach(value -> operators.add(value.asText()));
            assertTrue(operators.containsAll(field.operators()), field.fieldKey());
            assertFalse(field.operators().isEmpty(), field.fieldKey());
        }
    }

    @TestFactory Stream<DynamicTest> everyFieldAndOperatorValidatesAndExecutesItsCompiler() {
        return AdvancedFilterFieldCatalog.SCENES.stream().sorted().flatMap(scene -> service.catalog(scene).fields().stream()
                .flatMap(field -> field.operators().stream().map(operator -> DynamicTest.dynamicTest(
                        scene + " / " + field.fieldKey() + " / " + operator, () -> verifyOperator(scene, field, operator)))));
    }

    private void verifyOperator(String scene, AdvancedFilterCatalogRespVO.FieldVO field, String operator) {
        var condition = condition(field, operator);
        if (field.valueType().equals("duration")) {
            condition.setStartFieldKey(field.options().getFirst().value());
            condition.setEndFieldKey(field.options().getFirst().value());
            condition.setUnit("hour");
        }
        var group = new AdvancedFilterGroupReqVO(); group.setLogic("AND"); group.getConditions().add(condition);
        assertDoesNotThrow(() -> service.validate(scene, group));
        if (scene.equals("subordinate_sales")) {
            Object actual = operator.equals("is_empty") ? null : operator.equals("between") ? "8" : condition.getValue();
            if (actual instanceof List<?> values) actual = values.getFirst();
            if (actual == null && operator.equals("is_not_empty")) actual = "7";
            final Object rowValue = actual;
            boolean expected = !Set.of("not_in", "not_contains", "ne", "gt", "lt").contains(operator);
            assertEquals(expected, service.matches(scene, group, key -> rowValue));
            return;
        }
        TenantContextHolder.setTenantId(71L);
        try {
            AdvancedFilterQuery query = ReflectionTestUtils.invokeMethod(service, "buildIfPresent", group, scene,
                    Map.of("userId", 7L, "tenantReadAll", false));
            assertNotNull(query);
            String sql = query.getWhereSql();
            assertFalse(sql.isBlank());
            assertEquals(71L, query.getParameters().get("tenantId"));
            assertFalse(sql.contains(PROBE), "Values must be bound, never interpolated");
            assertFalse(sql.contains("validateOnly"));
            String marker = switch (operator) {
                case "contains", "not_contains" -> "LIKE";
                case "eq", "ne" -> " = "; case "gt" -> " > "; case "gte" -> " >= ";
                case "lt" -> " < "; case "lte" -> " <= "; case "between" -> "BETWEEN";
                case "in", "not_in" -> " IN ("; case "is_empty", "is_not_empty" -> "IS NULL";
                case "relative" -> " >= "; default -> throw new AssertionError("Missing SQL expectation for " + operator);
            };
            assertTrue(sql.contains(marker), sql);
            if (Set.of("not_in", "not_contains", "ne", "is_not_empty").contains(operator)) assertTrue(sql.contains("NOT"), sql);
            if (!Set.of("is_empty", "is_not_empty").contains(operator)) assertTrue(sql.contains("#{query.parameters."), sql);
            if (field.valueType().equals("duration")) assertTrue(sql.contains("TIMESTAMPDIFF"));
            else {
                var binding = AdvancedFilterFieldCatalog.fields().get(field.fieldKey()).bindings().get(scene);
                assertTrue(sql.contains(binding.expression()), field.fieldKey());
                if (binding.relation() != null) {
                    assertTrue(sql.contains("EXISTS")); assertTrue(sql.contains("tenant_id")); assertTrue(sql.contains("deleted"));
                }
            }
        } finally { TenantContextHolder.clear(); }
    }

    static AdvancedFilterConditionReqVO condition(AdvancedFilterCatalogRespVO.FieldVO field, String operator) {
        var c = new AdvancedFilterConditionReqVO(); c.setFieldKey(field.fieldKey()); c.setOperator(operator);
        String value = field.valueType().equals("date") ? "2026-09-23T12:00:00" : field.valueType().equals("text") ? PROBE : "7";
        if (operator.equals("in") || operator.equals("not_in")) c.setValue(List.of("7"));
        else if (operator.equals("relative")) c.setValue("today");
        else if (operator.equals("between")) {
            c.setValueFrom(field.valueType().equals("date") ? "2026-09-23T00:00:00" : "7");
            c.setValueTo(field.valueType().equals("date") ? "2026-09-24T00:00:00" : "9");
        } else if (!Set.of("is_empty", "is_not_empty").contains(operator)) c.setValue(value);
        return c;
    }

    @Test void restrictedAndForeignFieldsCannotBeSmuggledIntoRequests() throws Exception {
        for (String scene : AdvancedFilterFieldCatalog.SCENES) {
            var catalog = service.catalogWithoutVisibleUsers(scene);
            assertTrue(catalog.fields().stream().noneMatch(field -> "visible-users".equals(field.declaredOptionSource())));
            var forbidden = new ArrayList<String>();
            contract().get("forbiddenFields").forEach(node -> forbidden.add(node.asText()));
            AdvancedFilterFieldCatalog.fields().values().stream().filter(f -> !f.bindings().containsKey(scene))
                    .forEach(f -> forbidden.add(f.key()));
            for (String key : forbidden) {
                assertTrue(service.catalog(scene).fields().stream().noneMatch(field -> field.fieldKey().equals(key)));
                var c = new AdvancedFilterConditionReqVO(); c.setFieldKey(key); c.setOperator("eq"); c.setValue("7");
                var group = new AdvancedFilterGroupReqVO(); group.setLogic("AND"); group.getConditions().add(c);
                assertThrows(ServiceException.class, () -> service.validate(scene, group), scene + ":" + key);
            }
        }
    }

    @Test void sharedFrontendExamplesAreRealBackendWireContracts() throws Exception {
        for (var example : contract().get("wireExamples")) {
            var field = service.catalog(example.get("scene").asText()).fields().stream()
                    .filter(f -> f.fieldKey().equals(example.get("field").get("fieldKey").asText())).findFirst().orElseThrow();
            assertEquals(example.get("field"), JsonUtils.getObjectMapper().readTree(JsonUtils.toJsonString(field)));
            var group = JsonUtils.parseObject(example.get("filter").toString(), AdvancedFilterGroupReqVO.class);
            assertDoesNotThrow(() -> service.validate(example.get("scene").asText(), group));
        }
    }
}
