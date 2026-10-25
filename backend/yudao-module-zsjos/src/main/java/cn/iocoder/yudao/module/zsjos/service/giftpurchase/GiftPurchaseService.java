package cn.iocoder.yudao.module.zsjos.service.giftpurchase;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.service.gift.GiftConfigService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderGiftSnapshot;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderSnapshot;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.GiftPurchaseErrorCodes.*;

@Service
public class GiftPurchaseService {
    @Resource private GiftPurchaseMapper mapper;
    @Resource private GiftPurchaseOperationMapper operationMapper;
    @Resource private GiftConfigService giftConfigService;
    @Resource private AdminUserApi userApi;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderApprovalRoundMapper roundMapper;

    public PageResult<GiftPurchaseRespVO> page(GiftPurchasePageReqVO req) {
        var page = mapper.selectPage(new Page<GiftPurchaseDO>(req.getPageNo(), req.getPageSize()),
                Wrappers.lambdaQuery(GiftPurchaseDO.class)
                        .like(StrUtil.isNotBlank(req.getStudentName()), GiftPurchaseDO::getStudentName, req.getStudentName())
                        .like(StrUtil.isNotBlank(req.getOrderNo()), GiftPurchaseDO::getOrderNo, req.getOrderNo())
                        .like(StrUtil.isNotBlank(req.getGiftKeyword()), GiftPurchaseDO::getGiftItemsJson, req.getGiftKeyword())
                        .eq(req.getPurchased() != null, GiftPurchaseDO::getPurchased, req.getPurchased())
                        .orderByDesc(GiftPurchaseDO::getGeneratedAt).orderByDesc(GiftPurchaseDO::getId));
        return new PageResult<>(project(page.getRecords()), page.getTotal());
    }

    public GiftPurchaseRespVO get(Long id) { return project(List.of(require(id, false))).getFirst(); }

    public List<GiftPurchaseOperationRespVO> operations(Long id) {
        // History survives logical deletion and remains protected by detail permission and tenant scope.
        return operationMapper.selectList(Wrappers.lambdaQuery(GiftPurchaseOperationDO.class)
                .eq(GiftPurchaseOperationDO::getPurchaseId, id).orderByDesc(GiftPurchaseOperationDO::getId))
                .stream().map(row -> BeanUtils.toBean(row, GiftPurchaseOperationRespVO.class)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(GiftPurchaseSaveReqVO req) {
        GiftPurchaseDO row = new GiftPurchaseDO();
        apply(row, req);
        row.setGeneratedAt(LocalDateTime.now());
        row.setPurchased(false);
        mapper.insert(row);
        record(row.getId(), "CREATE");
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(GiftPurchaseSaveReqVO req) {
        GiftPurchaseDO row = require(req.getId(), true);
        apply(row, req);
        // Editable purchase fields never rewrite source identities or purchase marker evidence.
        mapper.update(null, Wrappers.lambdaUpdate(GiftPurchaseDO.class).eq(GiftPurchaseDO::getId, row.getId())
                .set(GiftPurchaseDO::getStudentName, row.getStudentName())
                .set(GiftPurchaseDO::getStudentMobile, row.getStudentMobile())
                .set(GiftPurchaseDO::getStudentWechatId, row.getStudentWechatId())
                .set(GiftPurchaseDO::getShippingAddress, row.getShippingAddress())
                .set(GiftPurchaseDO::getGiftItemsJson, row.getGiftItemsJson()));
        record(row.getId(), "UPDATE");
    }

    @Transactional(rollbackFor = Exception.class)
    public void setPurchased(Long id, boolean purchased) {
        GiftPurchaseDO row = require(id, true);
        if (Boolean.TRUE.equals(row.getPurchased()) == purchased) return;
        mapper.update(null, Wrappers.lambdaUpdate(GiftPurchaseDO.class).eq(GiftPurchaseDO::getId, id)
                .set(GiftPurchaseDO::getPurchased, purchased));
        record(id, purchased ? "PURCHASE" : "UNPURCHASE");
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id, true);
        mapper.deleteById(id);
        record(id, "DELETE");
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordGenerated(Long id) { record(id, "GENERATE"); }

    private void record(Long id, String action) {
        Long actorId = getLoginUserId();
        String name = "系统";
        if (actorId != null) {
            var actor = userApi.getUser(actorId);
            if (actor == null) throw exception(ACTOR_INVALID);
            name = actor.getNickname();
        }
        GiftPurchaseOperationDO entry = new GiftPurchaseOperationDO();
        entry.setPurchaseId(id); entry.setAction(action); entry.setOperatorUserId(actorId);
        entry.setOperatorName(name); entry.setOccurredAt(LocalDateTime.now());
        operationMapper.insert(entry);
    }

    private GiftPurchaseDO require(Long id, boolean lock) {
        GiftPurchaseDO row = id == null ? null : mapper.selectOne(Wrappers.lambdaQuery(GiftPurchaseDO.class)
                .eq(GiftPurchaseDO::getId, id).last(lock ? "FOR UPDATE" : ""));
        if (row == null) throw exception(NOT_FOUND);
        return row;
    }

    private void apply(GiftPurchaseDO row, GiftPurchaseSaveReqVO req) {
        var gifts = SalesOrderGiftSnapshot.resolve(req.getGiftItemCodes(), row.getGiftItemsJson(),
                () -> giftConfigService.listFlat(null), LocalDateTime.now());
        if (gifts.isEmpty()) throw exception(EMPTY_GIFTS);
        row.setStudentName(req.getStudentName().trim()); row.setStudentMobile(req.getStudentMobile());
        row.setStudentWechatId(req.getStudentWechatId()); row.setShippingAddress(req.getShippingAddress().trim());
        row.setGiftItemsJson(JsonUtils.toJsonString(gifts));

    }

    private List<GiftPurchaseRespVO> project(List<GiftPurchaseDO> rows) {
        if (rows.isEmpty()) return List.of();
        var orderIds = rows.stream().map(GiftPurchaseDO::getOrderId).filter(Objects::nonNull).distinct().toList();
        var orders = orderIds.isEmpty() ? List.<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO>of()
                : orderMapper.selectByIds(orderIds);
        var byOrder = orders.stream().collect(Collectors.toMap(o -> o.getId(), o -> o));
        var roundIds = orders.stream().map(o -> o.getCurrentApprovalRoundId()).filter(Objects::nonNull).distinct().toList();
        var rounds = roundIds.isEmpty() ? List.<cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO>of()
                : roundMapper.selectByIds(roundIds);
        var byRound = rounds.stream().collect(Collectors.toMap(r -> r.getId(), r -> r));
        var latest = new HashMap<Long, GiftPurchaseOperationDO>();
        var creators = new HashMap<Long, GiftPurchaseOperationDO>();
        var markers = new HashMap<Long, GiftPurchaseOperationDO>();
        operationMapper.selectList(Wrappers.lambdaQuery(GiftPurchaseOperationDO.class)
                .in(GiftPurchaseOperationDO::getPurchaseId, rows.stream().map(GiftPurchaseDO::getId).toList())
                .orderByDesc(GiftPurchaseOperationDO::getId)).forEach(op -> {
                    latest.putIfAbsent(op.getPurchaseId(), op);
                    // Descending order: overwrite CREATE for original creator, retain latest PURCHASE.
                    if ("CREATE".equals(op.getAction())) creators.put(op.getPurchaseId(), op);
                    if ("PURCHASE".equals(op.getAction())) markers.putIfAbsent(op.getPurchaseId(), op);
                });
        return rows.stream().map(row -> {
            GiftPurchaseRespVO result = BeanUtils.toBean(row, GiftPurchaseRespVO.class);
            try { result.setGiftItemSnapshots(SalesOrderGiftSnapshot.read(row.getGiftItemsJson())); }
            catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) { result.setGiftItemsInvalid(true); }
            result.setSourceType("UNKNOWN");
            if (row.getOrderId() != null) {
                result.setSourceType("ORDER");
                // Never infer historical salesperson names from current profiles or approval operators.
                result.setSubmitterUserId(null); result.setSubmitterUserName(null);
                var order = byOrder.get(row.getOrderId());
                if (order != null) {
                    var round = byRound.get(order.getCurrentApprovalRoundId());
                    var snapshot = SalesOrderSnapshot.read(round == null ? null : round.getOrderSnapshot(), order);
                    result.setSubmitterUserId(order.getSubmitterUserId());
                    result.setSubmitterUserName(snapshot.getSubmitter() == null ? null : snapshot.getSubmitter().name());
                    result.setSourceUserId(order.getSubmitterUserId());
                    result.setSourceUserName(result.getSubmitterUserName());
                }
            } else {
                // A missing order reference alone is not proof that someone manually created a row.
                var creator = creators.get(row.getId());
                if (creator != null) {
                    result.setSourceType("MANUAL");
                    result.setSourceUserId(creator.getOperatorUserId());
                    result.setSourceUserName(creator.getOperatorName());
                }
            }
            if (Boolean.TRUE.equals(row.getPurchased())) {
                var marker = markers.get(row.getId());
                if (marker != null) {
                    result.setPurchasedByUserId(marker.getOperatorUserId());
                    result.setPurchasedByName(marker.getOperatorName());
                    result.setPurchasedAt(marker.getOccurredAt());
                }
            }
            var op = latest.get(row.getId());
            if (op != null) { result.setOperatorName(op.getOperatorName()); result.setOperatedAt(op.getOccurredAt()); }
            return result;
        }).toList();
    }
}
