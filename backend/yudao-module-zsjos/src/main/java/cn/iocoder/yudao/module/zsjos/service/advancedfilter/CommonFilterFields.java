package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class CommonFilterFields {
    private CommonFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, text(Sensitivity.PERSONAL, "person.name", IDENTITY, "姓名", bind("lead", "p.name", personFromLead, "order", "p.name", personFromOrder, "lead_appeal", "p.name", personFromAppeal, "registration", "p.name", personFromRegistration, "student", "p.name", null)));
        add(result, text(Sensitivity.PERSONAL, "person.mobile", IDENTITY, "手机号", bind("lead", "p.mobile", personFromLead, "order", "p.mobile", personFromOrder, "lead_appeal", "p.mobile", personFromAppeal, "registration", "p.mobile", personFromRegistration, "student", "p.mobile", null)));
        add(result, text(Sensitivity.PERSONAL, "person.wechatId", IDENTITY, "微信号", bind("lead", "p.wechat_id", personFromLead, "order", "p.wechat_id", personFromOrder, "lead_appeal", "p.wechat_id", personFromAppeal, "registration", "p.wechat_id", personFromRegistration, "student", "p.wechat_id", null)));
        // RegistrationServiceImpl.complete records the formal identity as student, not a service lifecycle state.
        add(result, select(Sensitivity.STANDARD, "person.identityStatus", STATUS, "身份状态", options("lead", "潜在学员", "student", "正式学员"), bind("lead", "p.identity_status", personFromLead, "order", "p.identity_status", personFromOrder, "student", "p.identity_status", null)));
    }
}
