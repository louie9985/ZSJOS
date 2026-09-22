package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;
import java.time.LocalDateTime;

public record LeadCalendarCardRespVO(LeadManagementRespVO lead, LocalDateTime deadline,
                                    LeadFollowUpRespVO lastFollowUp, boolean canReadFollowUp) {}
