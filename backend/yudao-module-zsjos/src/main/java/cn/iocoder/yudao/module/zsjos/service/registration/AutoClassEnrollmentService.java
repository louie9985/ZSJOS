package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationCaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.AutoDeliveryClassService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_USER_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_ORDER_INVALID;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.STATUS_PROCESSING;

/**
 * Enrolls an effective order: one service per product item, in the class keyed by product + agreed exam month +
 * formal sales owner (the homeroom teacher, who is also the service owner). Registration fulfillment of these
 * {@link #ASSIGNMENT_MODE} cases only completes materials; it never chooses a class.
 */
@Service
public class AutoClassEnrollmentService {
    public static final String ASSIGNMENT_MODE = "auto_class";

    @Resource private RegistrationService registrationService;
    @Resource private RegistrationCaseMapper caseMapper;
    @Resource private SalesOrderItemMapper orderItemMapper;
    @Resource private ServiceRelationMapper serviceRelationMapper;
    @Resource private PersonMapper personMapper;
    @Resource private ZsjosProductMapper productMapper;
    @Resource private DeliveryClassService deliveryClassService;
    @Resource private AutoDeliveryClassService autoClasses;
    @Resource private RegistrationNotifyPublisher notifyPublisher;

    /**
     * Must join the transaction that marks the order effective (the caller holds the order row lock).
     * An owner who cannot be a homeroom teacher blocks the approval instead of silently leaving the student unplaced.
     */
    @Transactional(rollbackFor = Exception.class)
    public void enrollOnOrderEffective(SalesOrderDO order, LocalDateTime now) {
        if (order.getAgreedExamMonth() == null) return;
        AdminUserRespDTO homeroom = requireEligibleOwner(order.getFormalSalesUserId());
        registrationService.ensureCaseAfterRegistrationApproval(order.getId(), now);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<Long, SalesOrderItemDO> items = orderItemMapper.selectListByOrderIdForUpdate(order.getId(), tenantId).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        List<RegistrationCaseDO> cases = caseMapper.selectListByOrderId(order.getId()).stream()
                .filter(row -> ASSIGNMENT_MODE.equals(row.getAssignmentMode()) && row.getOrderItemId() != null
                        && Set.of(STATUS_PENDING, STATUS_PROCESSING).contains(row.getStatus()))
                .sorted(Comparator.comparing(RegistrationCaseDO::getId)).toList();
        PersonDO person = cases.isEmpty() || order.getPersonId() == null ? null
                : personMapper.selectByIdForUpdate(order.getPersonId(), tenantId);
        boolean enrolled = false;
        for (RegistrationCaseDO registrationCase : cases) {
            // Replays and retries keep the first enrollment; later moves belong to class transfer.
            if (!serviceRelationMapper.selectByCaseForUpdate(registrationCase.getId(), tenantId).isEmpty()) continue;
            SalesOrderItemDO item = items.get(registrationCase.getOrderItemId());
            if (item == null) throw exception(REGISTRATION_ORDER_INVALID);
            ZsjosProductDO product = item.getProductId() != null ? productMapper.selectById(item.getProductId())
                    : item.getProductRef() == null ? null : productMapper.selectByProductRef(item.getProductRef());
            String productName = productName(item, product);
            if (product == null || product.getId() == null) {
                throw exception0(REGISTRATION_ORDER_INVALID.getCode(), "订单商品「{}」对应的产品不存在，无法自动分班", productName);
            }
            DeliveryClassDO deliveryClass = autoClasses.ensure(product.getId(), productName, order.getAgreedExamMonth(), homeroom);
            ServiceRelationDO relation = new ServiceRelationDO();
            relation.setPersonId(order.getPersonId()); relation.setOrderId(order.getId()); relation.setOrderItemId(item.getId());
            relation.setRegistrationCaseId(registrationCase.getId()); relation.setStatus("active");
            relation.setStudentNameSnapshot(cn.hutool.core.util.StrUtil.trimToNull(order.getStudentName()));
            relation.setStudentNameSource("order_creation");
            relation.setOwnerUserId(homeroom.getId()); relation.setClassId(deliveryClass.getId());
            relation.setAcceptanceStatus("pending"); relation.setServiceSnapshot(item.getProductSnapshot());
            relation.setActivatedAt(now); relation.setVersion(0);
            serviceRelationMapper.insert(relation);
            notifyPublisher.publishPlannerAssigned(registrationCase, order, person == null ? null : person.getPersonNo(),
                    homeroom.getId(), order.getPersonId(), relation.getId());
            enrolled = true;
        }
        if (enrolled && person != null) {
            person.setIdentityStatus("student"); person.setLastSeenAt(now); person.setVersion(person.getVersion() + 1);
            personMapper.updateById(person);
        }
    }

    private AdminUserRespDTO requireEligibleOwner(Long ownerUserId) {
        if (ownerUserId == null) {
            throw exception0(DELIVERY_CLASS_USER_INVALID.getCode(), "订单缺少成交负责人，无法自动分班，请补正订单后再审批");
        }
        try {
            return deliveryClassService.validateHomeroom(ownerUserId);
        } catch (ServiceException invalid) {
            // Keep the stable account/qualification error codes; only add the approval context to the message.
            if (DELIVERY_CLASS_SALES_QUALIFICATION_REQUIRED.getCode().equals(invalid.getCode())) {
                throw exception0(invalid.getCode(), "成交负责人缺少销售业务资格，无法自动分班，请联系管理员配置后再审批");
            }
            if (DELIVERY_CLASS_USER_INVALID.getCode().equals(invalid.getCode())) {
                throw exception0(invalid.getCode(), "成交负责人账号不存在或已停用，无法自动分班，请联系管理员处理后再审批");
            }
            throw invalid;
        }
    }

    private static String productName(SalesOrderItemDO item, ZsjosProductDO product) {
        Map<?, ?> snapshot = JsonUtils.parseObjectQuietly(item.getProductSnapshot(), Map.class);
        if (snapshot != null && snapshot.get("name") instanceof String name && !name.isBlank()) return name.trim();
        return product != null && product.getName() != null ? product.getName() : "历史产品信息缺失";
    }
}
