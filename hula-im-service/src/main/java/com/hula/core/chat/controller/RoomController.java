package com.hula.core.chat.controller;


import com.hula.common.domain.vo.req.IdReqVO;
import com.hula.common.domain.vo.res.CursorPageBaseResp;
import com.hula.common.domain.vo.res.GroupListVO;
import com.hula.common.domain.vo.res.IdRespVO;
import com.hula.core.chat.domain.vo.request.ChatMessageMemberReq;
import com.hula.core.chat.domain.vo.request.GroupAddReq;
import com.hula.core.chat.domain.vo.request.admin.AdminAddReq;
import com.hula.core.chat.domain.vo.request.admin.AdminRevokeReq;
import com.hula.core.chat.domain.vo.request.member.MemberAddReq;
import com.hula.core.chat.domain.vo.request.member.MemberDelReq;
import com.hula.core.chat.domain.vo.request.member.MemberExitReq;
import com.hula.core.chat.domain.vo.request.member.MemberReq;
import com.hula.core.chat.domain.vo.response.ChatMemberListResp;
import com.hula.core.chat.domain.vo.response.MemberResp;
import com.hula.core.chat.service.IGroupMemberService;
import com.hula.core.chat.service.RoomAppService;
import com.hula.core.user.domain.vo.resp.ws.ChatMemberResp;
import com.hula.domain.vo.res.ApiResult;
import com.hula.utils.RequestHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 房间相关接口
 * @author nyh
 */
@RestController
@RequestMapping("/room")
@Tag(name = "聊天室相关接口")
@Slf4j
public class RoomController {
    @Resource
    private RoomAppService roomService;
    @Resource
    private IGroupMemberService groupMemberService;

    @GetMapping("/group")
    @Operation(summary ="群组详情")
    public ApiResult<MemberResp> groupDetail(@Valid IdReqVO request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.getGroupDetail(uid, request.getId()));
    }

    /**
     * 群聊列表
     *
     * @return {@link ApiResult }
     */
    @GetMapping("/group/list")
    public ApiResult<List<GroupListVO>> groupList(){
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(roomService.groupList(uid));
    }
    @GetMapping("/group/member/page")
    @Operation(summary ="群成员列表")
    public ApiResult<CursorPageBaseResp<ChatMemberResp>> getMemberPage(@Valid MemberReq request) {
        return ApiResult.success(roomService.getMemberPage(request));
    }

    @GetMapping("/group/member/list")
    @Operation(summary ="房间内的所有群成员列表-@专用")
    public ApiResult<List<ChatMemberListResp>> getMemberList(@Valid ChatMessageMemberReq request) {
        List<ChatMemberListResp> memberList = roomService.getMemberList(request);
        return ApiResult.success(memberList);
    }

    @DeleteMapping("/group/member")
    @Operation(summary ="移除成员")
    public ApiResult<Void> delMember(@Valid @RequestBody MemberDelReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.delMember(uid, request);
        return ApiResult.success();
    }

    @DeleteMapping("/group/member/exit")
    @Operation(summary ="退出群聊")
    public ApiResult<Boolean> exitGroup(@Valid @RequestBody MemberExitReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.exitGroup(uid, request);
        return ApiResult.success();
    }

    @PostMapping("/group")
    @Operation(summary ="新增群组")
    public ApiResult<IdRespVO> addGroup(@Valid @RequestBody GroupAddReq request) {
        Long uid = RequestHolder.get().getUid();
        Long roomId = roomService.addGroup(uid, request);
        return ApiResult.success(IdRespVO.builder().id(roomId).build());
    }

    @PostMapping("/group/member")
    @Operation(summary ="邀请好友")
    public ApiResult<Void> addMember(@Valid @RequestBody MemberAddReq request) {
        Long uid = RequestHolder.get().getUid();
        roomService.addMember(uid, request);
        return ApiResult.success();
    }

    @PutMapping("/group/admin")
    @Operation(summary ="添加管理员")
    public ApiResult<Boolean> addAdmin(@Valid @RequestBody AdminAddReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.addAdmin(uid, request);
        return ApiResult.success();
    }

    @DeleteMapping("/group/admin")
    @Operation(summary ="撤销管理员")
    public ApiResult<Boolean> revokeAdmin(@Valid @RequestBody AdminRevokeReq request) {
        Long uid = RequestHolder.get().getUid();
        groupMemberService.revokeAdmin(uid, request);
        return ApiResult.success();
    }


}
