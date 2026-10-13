package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarCreatorNamesTest {
    @InjectMocks CalendarCreatorNames names;
    @Mock AdminUserApi users;
    private CourseCalendarEventDO row(String creator) {
        var row = new CourseCalendarEventDO(); row.setCreator(creator); return row;
    }
    @Test void batchesUniqueCreatorsAndDoesNotInventMissingNames() {
        var user = new AdminUserRespDTO(); user.setId(7L); user.setNickname("创建员工");
        when(users.getUserMap(Set.of(7L,8L))).thenReturn(Map.of(7L,user));
        var result = names.resolve(Arrays.asList(row("7"),row("7"),row("8"),row(null),row("migration")));
        assertEquals("创建员工",result.get("7")); assertNull(result.get("8")); assertNull(result.get(null));
        verify(users).getUserMap(Set.of(7L,8L)); verifyNoMoreInteractions(users);
    }
    @Test void legacyAuditValuesDoNotBecomeUserIds() {
        var result=names.resolve(Arrays.asList(row(null),row("0"),row("-1"),row("999999999999999999999999"),row("migration")));
        assertTrue(result.isEmpty()); assertNull(result.get(null)); verifyNoInteractions(users);
    }
    @Test void courseProjectionPreservesAttachmentsAndExistingFields() {
        var service=new cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseCalendarEventService();
        var mapper=mock(cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper.class);
        var attachments=mock(cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseAttachmentService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"mapper",mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"attachments",attachments);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"creatorNames",names);
        var source=row("7").setId(2L).setCourseName("课程名称").setAttachmentIdsJson("[11]").setCalendarVersion(3);
        var user=new AdminUserRespDTO();user.setId(7L);user.setNickname("创建员工");
        when(users.getUserMap(Set.of(7L))).thenReturn(Map.of(7L,user));
        when(mapper.selectById(2L)).thenReturn(source);
        var result=service.get(2L);
        assertEquals("创建员工",result.getCreatorName()); assertEquals("课程名称",result.getCourseName());
        assertEquals(List.of(11L),result.getAttachmentIds()); assertEquals(3,result.getCalendarVersion());
        verify(attachments).describe("[11]");
    }
    @Test void personalProjectionKeepsCreatorDistinctFromOwner() {
        var service=new cn.iocoder.yudao.module.zsjos.service.personalcalendar.PersonalCalendarEventService();
        var mapper=mock(cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar.PersonalCalendarEventMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"mapper",mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"userApi",users);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"creatorNames",names);
        var creator=new AdminUserRespDTO();creator.setId(7L);creator.setNickname("创建员工");
        var owner=new AdminUserRespDTO();owner.setId(8L);owner.setNickname("所属员工");
        when(users.getUserMap(Set.of(7L))).thenReturn(Map.of(7L,creator));
        when(users.getUserMap(List.of(8L))).thenReturn(Map.of(8L,owner));
        var row=new cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar.PersonalCalendarEventDO().setId(1L).setOwnerUserId(8L);
        row.setCreator("7");
        var req=new cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventListReqVO();
        req.setRangeStart(java.time.LocalDateTime.of(2026,10,13,0,0));req.setRangeEnd(req.getRangeStart().plusDays(1));
        when(mapper.selectMyRange(8L,req.getRangeStart(),req.getRangeEnd())).thenReturn(List.of(row));
        var result=service.list(req,8L).getFirst();
        assertEquals("创建员工",result.getCreatorName());assertEquals("所属员工",result.getOwnerName());
    }
}
