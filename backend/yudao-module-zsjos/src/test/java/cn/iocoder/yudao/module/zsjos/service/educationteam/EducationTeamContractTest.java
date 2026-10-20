package cn.iocoder.yudao.module.zsjos.service.educationteam;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.EducationTeamController;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamOrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;
class EducationTeamContractTest {
 @Test void templateValidationAcceptsIndependentScene() throws Exception {
  var field=cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateSaveReqVO.class.getDeclaredField("scene");
  assertTrue("education_team".matches(field.getAnnotation(jakarta.validation.constraints.Pattern.class).regexp()));
  assertTrue("subordinate_sales".matches(field.getAnnotation(jakarta.validation.constraints.Pattern.class).regexp()));
 }
 @Test void everyEndpointUsesEducationPermissions(){
  for(var method:EducationTeamController.class.getDeclaredMethods()){if(method.getName().equals("navigationContext"))continue;var rule=method.getAnnotation(PreAuthorize.class);assertNotNull(rule,method.getName());assertTrue(rule.value().contains("zsjos:education-team:"));assertFalse(rule.value().contains("subordinate-sales"));}
 }
 @Test void filterCatalogDoesNotShareSalesFieldsOrDispatchConfiguration(){
  var service=new AdvancedFilterService();var catalog=service.catalog("education_team");assertFalse(catalog.fields().isEmpty());
  for(var field:catalog.fields()){assertTrue(field.fieldKey().startsWith("educationTeam."));assertFalse(field.fieldKey().contains("newcomer"));assertFalse(field.fieldKey().contains("accepting"));}
  assertTrue(catalog.fields().stream().filter(f->f.fieldKey().equals("educationTeam.todayFollowUpStatus")).flatMap(f->f.options().stream()).anyMatch(o->"incomplete".equals(o.value())));
  assertTrue(service.catalog("subordinate_sales").fields().stream().allMatch(field->field.fieldKey().startsWith("subordinate.")));
 }
 @Test void organizationSqlPassesTenantParser() throws Exception {
  var config=new org.apache.ibatis.session.Configuration();config.addMapper(EducationTeamOrganizationMapper.class);
  var sql=config.getMappedStatement(EducationTeamOrganizationMapper.class.getName()+".deliveryDepartments").getBoundSql(null).getSql();
  var parser=new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler(){public net.sf.jsqlparser.expression.Expression getTenantId(){return new net.sf.jsqlparser.expression.LongValue(1);}});
  String parsed=parser.parserSingle(sql,null);assertTrue(parsed.contains("tenant_id = 1"));assertTrue(parsed.contains("business_domain = 'delivery'"));
 }
 @Test void dailyFieldsAreEducationOnlyAndOldTemplatesRemainSupported() {
  var service=new AdvancedFilterService();
  var keys=java.util.Set.of("educationTeam.todayNewIntentionCount","educationTeam.todayOldIntentionFollowUpRecordCount","educationTeam.todayStudentContactCount");
  var fields=service.catalog("education_team").fields();
  for(var key:keys)assertTrue(fields.stream().anyMatch(field->key.equals(field.fieldKey())&&"number".equals(field.valueType())));
  assertTrue(fields.stream().anyMatch(field->"educationTeam.todayFollowUpRecordCount".equals(field.fieldKey())));
  assertTrue(service.catalog("subordinate_sales").fields().stream().noneMatch(field->keys.contains(field.fieldKey())));
 }
 @Test void dailyAndRecycledSalesSqlPassTenantParser() {
  var config=new org.apache.ibatis.session.Configuration();
  var edu=cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamDailyMapper.class;
  var sales=cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyMapper.class;
  config.addMapper(edu);config.addMapper(sales);
  var start=java.time.LocalDate.of(2026,10,19).atStartOfDay();
  var args=java.util.Map.of("tenant",1L,"users",java.util.List.of(20L),"start",start,"end",start.plusDays(1));
  var parser=new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler(){public net.sf.jsqlparser.expression.Expression getTenantId(){return new net.sf.jsqlparser.expression.LongValue(1);}});
  for(var mapper:java.util.List.of(edu,sales))for(var method:mapper.getDeclaredMethods()){
   var sql=config.getMappedStatement(mapper.getName()+"."+method.getName()).getBoundSql(args).getSql();
   assertTrue(parser.parserSingle(sql,null).contains("tenant_id = 1"),method.getName());
  }
  var sql="SELECT * FROM zsjos_lead WHERE "+cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesIdentitySql.CONDITION;
  assertTrue(parser.parserSingle(sql,null).contains("tenant_id = 1"));
 }
}
