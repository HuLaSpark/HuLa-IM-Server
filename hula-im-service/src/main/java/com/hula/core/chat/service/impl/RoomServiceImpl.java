package com.hula.core.chat.service.impl;

import com.hula.common.domain.po.RoomChatInfoPO;
import com.hula.common.domain.vo.res.GroupListVO;
import com.hula.common.enums.NormalOrNoEnum;
import com.hula.utils.AssertUtil;
import com.hula.core.chat.dao.GroupMemberDao;
import com.hula.core.chat.dao.RoomDao;
import com.hula.core.chat.dao.RoomFriendDao;
import com.hula.core.chat.dao.RoomGroupDao;
import com.hula.core.chat.domain.entity.GroupMember;
import com.hula.core.chat.domain.entity.Room;
import com.hula.core.chat.domain.entity.RoomFriend;
import com.hula.core.chat.domain.entity.RoomGroup;
import com.hula.core.chat.domain.enums.GroupRoleEnum;
import com.hula.core.chat.domain.enums.RoomTypeEnum;
import com.hula.core.chat.service.RoomService;
import com.hula.core.chat.service.adapter.ChatAdapter;
import com.hula.core.user.domain.entity.User;
import com.hula.core.user.service.cache.UserInfoCache;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


@Service
@AllArgsConstructor
public class RoomServiceImpl implements RoomService {

    private RoomFriendDao roomFriendDao;
    private RoomDao roomDao;
    private GroupMemberDao groupMemberDao;
    private UserInfoCache userInfoCache;
    private RoomGroupDao roomGroupDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomFriend createFriendRoom(List<Long> uidList) {
        AssertUtil.isNotEmpty(uidList, "房间创建失败，好友数量不对");
        AssertUtil.equal(uidList.size(), 2, "房间创建失败，好友数量不对");
        String key = ChatAdapter.generateRoomKey(uidList);

        RoomFriend roomFriend = roomFriendDao.getByKey(key);
        if (Objects.nonNull(roomFriend)) {
            //如果存在房间就恢复，适用于恢复好友场景
            restoreRoomIfNeed(roomFriend);
        } else {
            //新建房间
            Room room = createRoom(RoomTypeEnum.FRIEND);
            roomFriend = createFriendRoom(room.getId(), uidList);
        }
        return roomFriend;
    }

    @Override
    public RoomFriend getFriendRoom(Long uid1, Long uid2) {
        String key = ChatAdapter.generateRoomKey(Arrays.asList(uid1, uid2));
        return roomFriendDao.getByKey(key);
    }

    @Override
    public void disableFriendRoom(List<Long> uidList) {
        AssertUtil.isNotEmpty(uidList, "房间创建失败，好友数量不对");
        AssertUtil.equal(uidList.size(), 2, "房间创建失败，好友数量不对");
        String key = ChatAdapter.generateRoomKey(uidList);
        roomFriendDao.disableRoom(key);
    }

    @Override
    public RoomGroup createGroupRoom(Long uid, String groupName) {
        List<GroupMember> selfGroup = groupMemberDao.getSelfGroup(uid);
        AssertUtil.isTrue(selfGroup.size()<6, "每个人只能创建五个群");
        User user = userInfoCache.get(uid);
        Room room = createRoom(RoomTypeEnum.GROUP);
        // 插入群
        RoomGroup roomGroup = ChatAdapter.buildGroupRoom(user, room.getId(),groupName);
        roomGroupDao.save(roomGroup);
        // 插入群主
        GroupMember leader = GroupMember.builder()
                .role(GroupRoleEnum.LEADER.getType())
                .groupId(roomGroup.getId())
                .uid(uid)
                .build();
        groupMemberDao.save(leader);
        return roomGroup;
    }

    @Override
    public List<RoomChatInfoPO> chatInfo(Long uid, List<Long> roomIds, int type) {
        return roomDao.chatInfo(uid,roomIds,type);
    }

    @Override
    public List<GroupListVO> groupList(Long uid) {
        return roomDao.groupList(uid);
    }

    private RoomFriend createFriendRoom(Long roomId, List<Long> uidList) {
        RoomFriend insert = ChatAdapter.buildFriendRoom(roomId, uidList);
        roomFriendDao.save(insert);
        return insert;
    }

    private Room createRoom(RoomTypeEnum typeEnum) {
        Room insert = ChatAdapter.buildRoom(typeEnum);
        roomDao.save(insert);
        return insert;
    }

    private void restoreRoomIfNeed(RoomFriend room) {
        if (Objects.equals(room.getStatus(), NormalOrNoEnum.NOT_NORMAL.getStatus())) {
            roomFriendDao.restoreRoom(room.getId());
        }
    }
}
