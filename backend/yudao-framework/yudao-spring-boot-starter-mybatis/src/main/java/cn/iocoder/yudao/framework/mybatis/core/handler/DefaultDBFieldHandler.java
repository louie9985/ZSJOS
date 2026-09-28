package cn.iocoder.yudao.framework.mybatis.core.handler;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 通用参数填充实现类
 *
 * 如果没有显式的对通用参数进行赋值，这里会对通用参数进行填充、赋值
 *
 * @author hexiaowu
 */
public class DefaultDBFieldHandler implements MetaObjectHandler {

    /**
     * 无登录上下文（定时任务、消息消费等后台线程）时的操作人兜底值，对应 system_users.id=1（admin）。
     *
     * creator / updater 在部分表上是 NOT NULL，而 MyBatis-Plus 会把带 {@code fill} 的字段强制写进
     * INSERT 语句并显式绑定 NULL，从而覆盖掉 DDL 的 DEFAULT ''，触发 "Column 'creator' cannot be null"。
     * 这里不回填的话，后台线程的任何写入都会失败。
     */
    private static final String SYSTEM_OPERATOR = "1";

    @Override
    @SuppressWarnings("PatternVariableCanBeUsed")
    public void insertFill(MetaObject metaObject) {
        if (Objects.nonNull(metaObject) && metaObject.getOriginalObject() instanceof BaseDO) {
            BaseDO baseDO = (BaseDO) metaObject.getOriginalObject();

            LocalDateTime current = LocalDateTime.now();
            // 创建时间为空，则以当前时间为插入时间
            if (Objects.isNull(baseDO.getCreateTime())) {
                baseDO.setCreateTime(current);
            }
            // 更新时间为空，则以当前时间为更新时间
            if (Objects.isNull(baseDO.getUpdateTime())) {
                baseDO.setUpdateTime(current);
            }
            // ZSJOS LeadDO uses this optional field as the server-owned inbox activity anchor.
            if (metaObject.hasSetter("lastActivityAt") && Objects.isNull(getFieldValByName("lastActivityAt", metaObject))) {
                setFieldValByName("lastActivityAt", current, metaObject);
            }

            Long userId = SecurityFrameworkUtils.getLoginUserId();
            // 当前登录用户不为空，则以当前登录用户为创建人/更新人；否则回落到系统操作人，避免向 NOT NULL 列写入 NULL
            String operator = Objects.nonNull(userId) ? userId.toString() : SYSTEM_OPERATOR;
            // 创建人为空，则填充操作人
            if (Objects.isNull(baseDO.getCreator())) {
                baseDO.setCreator(operator);
            }
            // 更新人为空，则填充操作人
            if (Objects.isNull(baseDO.getUpdater())) {
                baseDO.setUpdater(operator);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时间为空，则以当前时间为更新时间
        Object modifyTime = getFieldValByName("updateTime", metaObject);
        if (Objects.isNull(modifyTime)) {
            setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
        if (metaObject.hasSetter("lastActivityAt") && Objects.isNull(getFieldValByName("lastActivityAt", metaObject))) {
            setFieldValByName("lastActivityAt", LocalDateTime.now(), metaObject);
        }

        // 当前登录用户不为空，则以当前登录用户为更新人；否则回落到系统操作人
        Object modifier = getFieldValByName("updater", metaObject);
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (Objects.isNull(modifier)) {
            setFieldValByName("updater", Objects.nonNull(userId) ? userId.toString() : SYSTEM_OPERATOR, metaObject);
        }
    }
}
