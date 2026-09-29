package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.Map;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;

/** All conditions bind to one account row; a Person match requires one readable matching account. */
final class MediaStudentFilterFields {
    private MediaStudentFilterFields() {}
    static void register(Map<String, Field> fields) {
        add(fields, selectSource(Sensitivity.PERSONAL, "mediaAccount.ownerOperatorUserId", PEOPLE,
                "责任运营", "visible-users", bind("media_student", "ma.owner_operator_user_id", null)));
        add(fields, selectSource(Sensitivity.STANDARD, "mediaAccount.platform", STATUS,
                "账号平台", "dict:zsjos_account_platform", bind("media_student", "ma.platform_value", null)));
        add(fields, selectSource(Sensitivity.STANDARD, "mediaAccount.currentStatus", STATUS,
                "账号状态", "dict:zsjos_media_account_current_status", bind("media_student", "ma.current_status_value", null)));
    }
}
