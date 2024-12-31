package com.hula.core.chat.domain.vo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新建群组
 * @author nyh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupAddReq {
    @NotNull
    @Size(min = 1, max = 50,message = "只能邀请1-50人")
    @Schema(description ="邀请的uid")
    private List<Long> uidList;

    @Schema(description ="群聊名称")
    private String groupName;
}
