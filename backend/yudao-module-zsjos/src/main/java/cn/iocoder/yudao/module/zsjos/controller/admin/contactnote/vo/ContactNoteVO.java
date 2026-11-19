package cn.iocoder.yudao.module.zsjos.controller.admin.contactnote.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public class ContactNoteVO {
    public record Append(@NotBlank @Size(max = 2000) String content,
                         @NotBlank @Size(max = 64) String requestKey) {}
    public record Entry(Long id, String authorName, LocalDateTime createdAt, String content) {}
    public record Page(List<Entry> list, Long total, boolean canAppend) {}
}
