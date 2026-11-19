package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.AutoDeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_VERSION_CONFLICT;

/**
 * Finds or creates the ordinary class keyed by tenant + product + agreed exam month + homeroom teacher.
 * Callers run inside the order-effective transaction; the unique key {@code uk_delivery_auto_class}
 * makes concurrent approvals converge on one class instead of creating duplicates.
 */
@Service
public class AutoDeliveryClassService {
    private static final int CLASS_NAME_MAX = 100;
    private static final int NUMBER_ATTEMPTS = 20;

    @Resource private DeliveryClassMapper classes;
    @Resource private AutoDeliveryClassMapper autoClasses;
    @Resource private DeliveryClassNumberService numbers;
    @Resource private DeptApi depts;

    /** {@code homeroom} must already have passed {@link DeliveryClassService#validateHomeroom(Long)}. */
    public DeliveryClassDO ensure(Long productId, String productName, String examMonth, AdminUserRespDTO homeroom) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        YearMonth month = YearMonth.parse(examMonth);
        DeliveryClassDO existing = autoClasses.selectByKey(tenantId, productId, examMonth, homeroom.getId());
        if (existing != null) {
            // Serialize enrollment with class completion/edit; the snapshot row may be stale.
            DeliveryClassDO locked = classes.selectByIdForUpdate(existing.getId(), tenantId);
            if (matches(locked, productId, examMonth, homeroom.getId())) return locked;
        }
        DeptRespDTO dept = homeroom.getDeptId() == null ? null : depts.getDept(homeroom.getDeptId());
        for (int attempt = 0; attempt < NUMBER_ATTEMPTS; attempt++) {
            DeliveryClassDO row = new DeliveryClassDO().setClassNo(numbers.next())
                    .setClassName(className(productName, month)).setSystemClass(false)
                    .setClassKind(DeliveryClassKind.NORMAL).setProductId(productId).setProductNameSnapshot(productName)
                    .setExamMonth(examMonth)
                    // Same date-range grammar as manually created classes so date filters and progress views work.
                    .setExamScheduleSnapshot(month.atDay(1) + "~" + month.atEndOfMonth())
                    .setHomeroomUserId(homeroom.getId()).setHomeroomUserNameSnapshot(homeroom.getNickname())
                    .setDeptId(homeroom.getDeptId()).setDeptNameSnapshot(dept == null ? null : dept.getName())
                    .setStatus("SERVING").setVersion(0);
            try {
                classes.insert(row);
                return row;
            } catch (DuplicateKeyException conflict) {
                // Either another approval created the same key (adopt it) or the class number collided (retry).
                DeliveryClassDO winner = autoClasses.selectByKeyForUpdate(tenantId, productId, examMonth, homeroom.getId());
                if (winner != null) return winner;
            }
        }
        throw exception(DELIVERY_CLASS_VERSION_CONFLICT);
    }

    static String className(String productName, YearMonth month) {
        String suffix = " " + month.getYear() + "年" + month.getMonthValue() + "月";
        String name = productName == null || productName.isBlank() ? "未命名产品" : productName.trim();
        return name.length() + suffix.length() > CLASS_NAME_MAX
                ? name.substring(0, CLASS_NAME_MAX - suffix.length()) + suffix : name + suffix;
    }

    private static boolean matches(DeliveryClassDO row, Long productId, String examMonth, Long homeroomUserId) {
        return row != null && "SERVING".equals(row.getStatus()) && DeliveryClassKind.NORMAL.equals(row.getClassKind())
                && Objects.equals(row.getProductId(), productId) && Objects.equals(row.getExamMonth(), examMonth)
                && Objects.equals(row.getHomeroomUserId(), homeroomUserId);
    }
}
