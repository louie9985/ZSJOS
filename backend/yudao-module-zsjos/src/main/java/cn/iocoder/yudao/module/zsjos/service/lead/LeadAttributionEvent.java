package cn.iocoder.yudao.module.zsjos.service.lead;

/**
 * 派单轨迹的绩效埋点事件。
 *
 * <p>绩效快照是旁路统计，不应与客资流转共用一个事务：埋点写入失败会把派单超时回收等主流程一并回滚。
 * 因此改为发布事件、由 {@link LeadAttributionEventListener} 在事务提交后消费。
 */
record LeadAttributionEvent(String factType, Long factId, Long leadId, Long userId) {
}
