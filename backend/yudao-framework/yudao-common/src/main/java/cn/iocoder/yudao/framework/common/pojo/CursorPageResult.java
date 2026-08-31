package cn.iocoder.yudao.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Schema(description = "游标分页结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public final class CursorPageResult<T> implements Serializable {

    @Schema(description = "数据", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> list;

    @Schema(description = "下一页游标")
    private String nextCursor;

    @Schema(description = "是否还有更多数据", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasMore;

}
