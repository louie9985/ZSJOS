package cn.iocoder.yudao.module.zsjos.service.calendar;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarPaletteRespVO.Color;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarPaletteDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarPaletteMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class CalendarPaletteServiceTest {
 @Mock CalendarPaletteMapper mapper;
 @Mock CalendarPalettePermissionProvider access;
 @InjectMocks CalendarPaletteService service;
 @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
 @AfterEach void cleanup() { TenantContextHolder.clear(); }
 CalendarPaletteDO row(List<Color> colors) {
  return new CalendarPaletteDO().setId(1L).setUserId(7L).setCalendarType("PERSONAL").setColorsJson(JsonUtils.toJsonString(colors));
 }
 CalendarColorSaveReqVO req(String name,String color) { return new CalendarColorSaveReqVO().setName(name).setColor(color); }
 @Test void normalizeAndRejectUnsafeColors() {
  assertEquals("#AABBCC",CalendarColors.resolve("#aabbcc",null));
  assertEquals("#112233",CalendarColors.resolve(null,"#112233"));
  assertNull(CalendarColors.resolve("","#112233"));
  for(String invalid:List.of("red","#fff","#11223344","url(test)")) assertThrows(ServiceException.class,()->CalendarColors.resolve(invalid,null));
 }
 @Test void appendRenameDeleteAreIndependentOfEvents() {
  var row=row(List.of()); when(mapper.lock(1L,7L,"PERSONAL")).thenReturn(row);
  var result=service.mutate("PERSONAL",7L,null,req(" 会议 ","#aabbcc"),false);
  assertEquals("会议",result.colors().get(0).name()); assertEquals("#AABBCC",result.colors().get(0).color());
  String id=result.colors().get(0).id();
  assertEquals("培训",service.mutate("PERSONAL",7L,id,req("培训","#112233"),false).colors().get(0).name());
  assertTrue(service.mutate("PERSONAL",7L,id,null,true).colors().isEmpty());
  verify(mapper,times(3)).ensure(1L,7L,"PERSONAL");
 }
 @Test void duplicateLimitAndMissingRejectWithoutUpdate() {
  var colors=new ArrayList<Color>(); for(int i=0;i<20;i++) colors.add(new Color(""+i,"颜色"+i,"#112233"));
  when(mapper.lock(1L,7L,"PERSONAL")).thenReturn(row(colors));
  assertEquals(CalendarColors.NAME.getCode(),assertThrows(ServiceException.class,()->service.mutate("PERSONAL",7L,null,req("颜色0","#112233"),false)).getCode());
  assertEquals(CalendarColors.LIMIT.getCode(),assertThrows(ServiceException.class,()->service.mutate("PERSONAL",7L,null,req("新增","#112233"),false)).getCode());
  assertEquals(CalendarColors.MISSING.getCode(),assertThrows(ServiceException.class,()->service.mutate("PERSONAL",7L,"foreign",null,true)).getCode());
  verify(mapper,never()).updateById(any(CalendarPaletteDO.class));
 }
 @Test void deniedStopsBeforeStorage() {
  doThrow(new ServiceException(CalendarColors.DENIED)).when(access).check(7L,"COURSE:write",7L);
  assertThrows(ServiceException.class,()->service.mutate("COURSE",7L,null,req("x","#112233"),false));
  verifyNoInteractions(mapper);
 }
}
