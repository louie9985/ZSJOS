package cn.iocoder.yudao.module.zsjos.service.advancedfilter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PublicSeaFilterFieldsTest {
 @Test void currentCycleIsTenantBoundAndHistoricalCyclesCannotMatch() {
  var fields = AdvancedFilterFieldCatalog.fields();
  for (String key : List.of("originalOwnerUserId", "collaboratorUserId", "frozenDeptId", "cycleNo", "ownershipStartedAt", "enteredAt", "assignedAt", "dueAt")) {
   var field = fields.get("publicSea." + key);
   assertNotNull(field);
   var sql = field.bindings().get("lead").expression();
   for (String guard : List.of("psc.lead_id=l.id", "psc.tenant_id=l.tenant_id", "psc.deleted=b'0'", "'waiting_assignment','assigned','deal_pending'", "ORDER BY psc.id DESC LIMIT 1")) assertTrue(sql.contains(guard));
   assertEquals(1, field.bindings().size());
  }
 }
 @Test void catalogUsesSupportedTypesAndScopedOptions() {
  var fields = new AdvancedFilterService().catalog("lead", List.of()).fields().stream().filter(f -> f.fieldKey().startsWith("publicSea.")).toList();
  assertEquals(8, fields.size());
  fields.forEach(f -> assertTrue(List.of("select", "number", "date").contains(f.valueType())));
  fields.stream().filter(f -> "visible-users".equals(f.declaredOptionSource())).forEach(f -> { assertTrue(f.options().isEmpty()); assertNull(f.optionSource()); });
 }
}
