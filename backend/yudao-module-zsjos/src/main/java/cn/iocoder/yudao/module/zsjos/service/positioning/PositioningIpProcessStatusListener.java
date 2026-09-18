package cn.iocoder.yudao.module.zsjos.service.positioning;
import cn.iocoder.yudao.module.bpm.api.event.*; import jakarta.annotation.Resource; import org.springframework.stereotype.Component; import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.PROCESS_KEY_POSITIONING_IP;
/**
 * IP（专业）审核流程的状态监听器。
 *
 * <p><b>已退役分支的遗留件，不要据此认为该流程仍在用。</b>新提交不再发起
 * {@code zsjos_media_positioning_ip}（见 {@link PositioningCardService#submitReview}
 * 与 {@link PositioningCardService#handleIpProcessResult} 的说明）；本监听器与
 * {@code script/bpm/zsjos_media_positioning_ip/} 下的 BPMN 资产仅为**在途实例**保留。
 *
 * <p>当下没有新实例写入 {@code ipProcessInstanceId}，因此事件到达时
 * {@code selectByIpProcessId} 通常查不到 card，回调直接返回——这是预期行为。
 */
@Component public class PositioningIpProcessStatusListener extends BpmProcessInstanceStatusEventListener { @Resource private PositioningCardService service; @Override protected String getProcessDefinitionKey(){return PROCESS_KEY_POSITIONING_IP;} @Override protected void onEvent(BpmProcessInstanceStatusEvent event){service.handleIpProcessResult(event.getId(),event.getStatus(),event.getReason());} }
