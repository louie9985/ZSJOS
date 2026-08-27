package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.mediascreen.MediaScreenDailySnapshotDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.MediaScreenContributionRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
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
    private final PartnerMapper partnerMapper=mock(PartnerMapper.class);
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
        when(partnerMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                new PartnerDO().setId(101L).setName("合作方甲").setStatus("enabled"),
                new PartnerDO().setId(102L).setName("合作方乙").setStatus("disabled")));
        service=new MediaScreenQueryService(leadMapper,snapshotMapper,partnerMapper,userApi,deptApi,
                mock(MaintenanceModeApi.class),redis,properties);
    }

    @Test void statsKeepsDirectAndPartTimeExclusiveAndSwitchControlsAllSummaryMetrics(){
        when(leadMapper.countMediaScreenContributions(anyLong(),any(),any(),any(),any())).thenReturn(List.of(
                row(3L,10L,"direct",null,null,2,3,4,2),
                row(4L,20L,"direct",null,null,1,2,3,1),
                row(3L,10L,"part_time",101L,"合作方甲",5,6,7,3)));

        var result=service.stats(1L,false);

        assertEquals(7,result.getSummary().getMonthTotal());
        assertEquals(3,result.getSummary().getMonthEffective());
        assertEquals(2,result.getDepartments().size());
        assertEquals(4,result.getDepartments().get(0).getMembers().stream().filter(x->x.getName().equals("成员甲")).findFirst().orElseThrow().getMonthTotal());
        assertNull(result.getPartTimeCompanionDepartment());
        assertFalse(result.isPartTimeIncluded());

        var included=service.stats(1L,true);
        assertEquals(14,included.getSummary().getMonthTotal());
        assertEquals(6,included.getSummary().getMonthEffective());
        assertEquals(7,included.getPartTimeCompanionDepartment().getMetrics().getMonthTotal());
        assertEquals(1,included.getPartTimeCompanionDepartment().getMembers().get(0).getPartTimers().size());
        assertEquals(7,included.getTodayStar().getToday());
        assertTrue(included.getTodayStar().isIncludesPartTime());
    }

    @Test void historyUsesFrozenCumulativeMetricsAndSnapshotAccountStatus(){
        LocalDate date=LocalDate.of(2026,8,26);
        MediaScreenDailySnapshotDO direct=snapshot(date,"direct",10L,3L,"成员甲","新媒体一部",true,5,7,11,4,null);
        MediaScreenDailySnapshotDO disabled=snapshot(date,"direct",10L,5L,"停用成员","新媒体一部",false,2,2,2,1,null);
        MediaScreenDailySnapshotDO part=snapshot(date,"part_time",10L,3L,"成员甲","新媒体一部",true,3,4,6,2,"[]");
        when(snapshotMapper.selectByDate(1L,date)).thenReturn(List.of(direct,disabled,part));

        var result=service.history(1L,date,true);

        assertTrue(result.isAvailable()); assertTrue(result.isPartTimeIncluded());
        assertEquals(10,result.getSummary().getToday()); assertEquals(13,result.getSummary().getWeek());
        assertEquals(19,result.getSummary().getMonthTotal()); assertEquals(7,result.getSummary().getMonthEffective());
        assertEquals(1,result.getDepartments().get(0).getMembers().size());
        assertEquals(8,result.getTodayStar().getToday());
        assertEquals("persisted_snapshot_v2",result.getSource());
        verifyNoInteractions(leadMapper);
    }

    @Test void freezeDoesNotOverwriteExistingDateSnapshot(){
        LocalDate date=LocalDate.of(2026,8,25);
        when(snapshotMapper.selectByDate(1L,date)).thenReturn(List.of(snapshot(date,"direct",10L,3L,
                "成员甲","新媒体一部",true,1,1,1,1,null)));

        service.freeze(1L,date);

        verifyNoInteractions(leadMapper);
        verify(snapshotMapper,never()).insertIgnore(anyLong(),any());
    }

    private static MediaScreenContributionRow row(long user,long dept,String type,Long provider,String providerName,long today,long week,long month,long effective){var r=new MediaScreenContributionRow();r.setContributorUserId(user);r.setContributorName(user==3L?"成员甲":"成员乙");r.setSourceDeptId(dept);r.setDepartmentName(dept==10L?"新媒体一部":"新媒体二部");r.setContributionType(type);r.setProviderOwnerId(provider);r.setProviderOwnerName(providerName);r.setTodayCount(today);r.setWeekCount(week);r.setMonthTotal(month);r.setMonthEffective(effective);return r;}
    private static DeptRespDTO dept(long id,String name,long leader){return new DeptRespDTO().setId(id).setName(name).setLeaderUserId(leader);}
    private static AdminUserRespDTO user(long id,long dept,String name){var u=new AdminUserRespDTO();u.setId(id);u.setDeptId(dept);u.setNickname(name);u.setStatus(0);return u;}
    private static MediaScreenDailySnapshotDO snapshot(LocalDate date,String type,long deptId,long member,String name,
                                                        String dept,boolean enabled,int today,int week,int month,
                                                        int effective,String partnerJson){var row=new MediaScreenDailySnapshotDO()
            .setSnapshotDate(date).setContributionType(type).setDepartmentId(deptId).setMemberId(member)
            .setMemberName(name).setMemberEnabled(enabled).setDepartmentName(dept).setTodayCount(today)
            .setWeekCount(week).setMonthTotal(month).setMonthEffective(effective).setPartnerDetailsJson(partnerJson);
        row.setCreateTime(LocalDateTime.now());return row;}
}
