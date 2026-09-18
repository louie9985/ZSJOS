package cn.iocoder.yudao.module.zsjos.dal.mysql.account;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountProfileEntryDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface MediaAccountProfileEntryMapper extends BaseMapperX<MediaAccountProfileEntryDO> {
    default MediaAccountProfileEntryDO latestDiagnosis(Long accountId) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountProfileEntryDO>()
                .eq(MediaAccountProfileEntryDO::getAccountId, accountId).eq(MediaAccountProfileEntryDO::getKind, "DIAGNOSIS")
                .orderByDesc(MediaAccountProfileEntryDO::getId).last("LIMIT 1"));
    }
    default MediaAccountProfileEntryDO replay(Long accountId, Long userId, String key) {
        return selectOne(new LambdaQueryWrapperX<MediaAccountProfileEntryDO>()
            .eq(MediaAccountProfileEntryDO::getAccountId,accountId).eq(MediaAccountProfileEntryDO::getOperatedByUserId,userId)
            .eq(MediaAccountProfileEntryDO::getIdempotencyKey,key));
    }
    default MediaAccountProfileEntryDO latestField(Long accountId, String fieldKey) {
        var keys = "adjustment_28d".equals(fieldKey) ? java.util.List.of("adjustment_28d", "diagnosis_28d") : java.util.List.of(fieldKey);
        return selectOne(new LambdaQueryWrapperX<MediaAccountProfileEntryDO>().eq(MediaAccountProfileEntryDO::getAccountId, accountId)
                .in(MediaAccountProfileEntryDO::getFieldKey, keys).orderByDesc(MediaAccountProfileEntryDO::getId).last("LIMIT 1"));
    }
    default PageResult<MediaAccountProfileEntryDO> filteredPage(Long accountId, cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.HistoryQuery page) {
        var query = new LambdaQueryWrapperX<MediaAccountProfileEntryDO>().eq(MediaAccountProfileEntryDO::getAccountId, accountId)
                .eqIfPresent(MediaAccountProfileEntryDO::getKind, page.getKind());
        if (page.getFieldKey() != null) query.in(MediaAccountProfileEntryDO::getFieldKey,
                "adjustment_28d".equals(page.getFieldKey()) ? java.util.List.of("adjustment_28d", "diagnosis_28d") : java.util.List.of(page.getFieldKey()));
        if (page.getCycle() != null) query.apply("JSON_VALID(content) AND JSON_EXTRACT(IF(JSON_VALID(content),content,'{}'),'$.cycle') = {0}", page.getCycle());
        return selectPage(page, query.orderByDesc(MediaAccountProfileEntryDO::getId));
    }
    default PageResult<MediaAccountProfileEntryDO> page(Long accountId, PageParam page) {
        return selectPage(page,new LambdaQueryWrapperX<MediaAccountProfileEntryDO>().eq(MediaAccountProfileEntryDO::getAccountId,accountId)
            .orderByDesc(MediaAccountProfileEntryDO::getId));
    }
    default PageResult<MediaAccountProfileEntryDO> positioningPage(Long accountId, PageParam page) {
        return selectPage(page, new LambdaQueryWrapperX<MediaAccountProfileEntryDO>()
                .eq(MediaAccountProfileEntryDO::getAccountId, accountId)
                .eq(MediaAccountProfileEntryDO::getKind, "POSITIONING")
                .orderByDesc(MediaAccountProfileEntryDO::getId));
    }
}
