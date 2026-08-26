package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.mediascreen.MediaScreenDailySnapshotDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.MediaScreenContributionRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.mediascreen.MediaScreenDailySnapshotMapper;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaScreenQueryServiceTest {
    private final LeadMapper leadMapper=mock(LeadMapper.class);
    private final MediaScreenDailySnapshotMapper snapshotMapper=mock(MediaScreenDailySnapshotMapper.class);
    private final AdminUserApi userApi=mock(AdminUserApi.class);
    private final DeptApi deptApi=mock(DeptApi.class);
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    private final MediaScreenProperties properties=new MediaScreenProperties();
    private MediaScreenQueryService service;

    @BeforeEach void setUp(){
        properties.getNewMedia().setDepartmentIds(List.of(10L,20L));
        when(deptApi.getDeptMap(anyCollection())).thenReturn(Map.of(10L,dept(10L,"新媒体一部",1L),20L,dept(20L,"新媒体二部",2L)));
        Map<Long,AdminUserRespDTO> users=Map.of(1L,user(1L,10L,"主管甲"),2L,user(2L,20L,"主管乙"),3L,user(3L,10L,"成员甲"),4L,user(4L,20L,"成员乙"));
        when(userApi.getUserMap(anyCollection())).thenAnswer(inv->{Map<Long,AdminUserRespDTO> out=new HashMap<>();for(Long id:(Collection<Long>)inv.getArgument(0))if(users.containsKey(id))out.put(id,users.get(id));return out;});
        when(userApi.getUserListByDeptIds(anyCollection())).thenReturn(List.of(users.get(1L),users.get(2L),users.get(3L),users.get(4L)));
        when(leadMapper.countMediaScreenTenMinuteContributions(anyLong(),any(),any())).thenReturn(List.of());
        when(leadMapper.countMediaScreenDailyContributions(anyLong(),any(),any())).thenReturn(List.of());
        when(snapshotMapper.selectByDate(anyLong(),any())).thenReturn(List.of());
        service=new MediaScreenQueryService(leadMapper,snapshotMapper,userApi,deptApi,mock(MaintenanceModeApi.class),redis,properties);
    }

    @Test void statsAggregatesInternalAndRecordedProviderContribution(){
        when(leadMapper.countMediaScreenContributions(anyLong(),any(),any(),any(),any())).thenReturn(List.of(
                row(3L,10L,"internal_new_media",null,2,3,4,2),
                row(4L,999L,"sales_self_sourced",4L,1,2,3,1),
                row(3L,999L,"internal_new_media",null,9,9,9,9)));

        var result=service.stats(1L,false);

        assertEquals(7,result.getSummary().getMonthTotal());
        assertEquals(3,result.getSummary().getMonthEffective());
        assertEquals(2,result.getDepartments().size());
        assertEquals(4,result.getDepartments().get(0).getMembers().stream().filter(x->x.getName().equals("成员甲")).findFirst().orElseThrow().getMonthTotal());
        assertEquals(3,result.getDepartments().get(1).getMembers().stream().filter(x->x.getName().equals("成员乙")).findFirst().orElseThrow().getMonthTotal());
        assertNull(result.getPartTimeCompanionDepartment());
        assertFalse(result.isPartTimeIncluded());
    }

    @Test void historyUsesOnlyFrozenRowsAndAccumulatesWeekAndMonth(){
        LocalDate date=LocalDate.of(2026,8,26);
        MediaScreenDailySnapshotDO monday=snapshot(date.minusDays(2),3L,"成员甲","新媒体一部",2,1);
        MediaScreenDailySnapshotDO today=snapshot(date,3L,"成员甲","新媒体一部",5,2);
        when(snapshotMapper.selectByDate(1L,date)).thenReturn(List.of(today));
        when(snapshotMapper.selectByDateBetween(1L,date.withDayOfMonth(1),date)).thenReturn(List.of(monday,today));

        var result=service.history(1L,date,true);

        assertTrue(result.isAvailable()); assertTrue(result.isPartTimeIncluded());
        assertEquals(5,result.getSummary().getToday()); assertEquals(7,result.getSummary().getWeek());
        assertEquals(7,result.getSummary().getMonthTotal()); assertEquals(3,result.getSummary().getMonthEffective());
        verifyNoInteractions(leadMapper);
    }

    @Test void freezeDoesNotOverwriteExistingMemberSnapshot(){
        when(leadMapper.countMediaScreenContributions(anyLong(),any(),any(),any(),any())).thenReturn(List.of());
        when(snapshotMapper.selectMemberIds(eq(1L),any(),anyCollection())).thenReturn(List.of(1L,2L,3L,4L));

        service.freeze(1L,LocalDate.of(2026,8,25));

        verify(snapshotMapper,never()).insertIgnore(anyLong(),any());
    }

    private static MediaScreenContributionRow row(long user,long dept,String type,Long provider,long today,long week,long month,long effective){var r=new MediaScreenContributionRow();r.setContributorUserId(user);r.setSourceDeptId(dept);r.setSourceType(type);r.setSourceProviderUserId(provider);r.setTodayCount(today);r.setWeekCount(week);r.setMonthTotal(month);r.setMonthEffective(effective);return r;}
    private static DeptRespDTO dept(long id,String name,long leader){return new DeptRespDTO().setId(id).setName(name).setLeaderUserId(leader);}
    private static AdminUserRespDTO user(long id,long dept,String name){var u=new AdminUserRespDTO();u.setId(id);u.setDeptId(dept);u.setNickname(name);u.setStatus(0);return u;}
    private static MediaScreenDailySnapshotDO snapshot(LocalDate date,long member,String name,String dept,int submitted,int valid){var row=new MediaScreenDailySnapshotDO().setSnapshotDate(date).setMemberId(member).setMemberName(name).setDepartmentName(dept).setSubmittedCount(submitted).setValidCount(valid);row.setCreateTime(LocalDateTime.now());return row;}
}
