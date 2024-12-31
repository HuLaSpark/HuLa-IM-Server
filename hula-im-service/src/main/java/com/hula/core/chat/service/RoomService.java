package com.hula.core.chat.service;


import com.hula.common.domain.po.RoomChatInfoPO;
import com.hula.common.domain.vo.res.GroupListVO;
import com.hula.core.chat.domain.entity.RoomFriend;
import com.hula.core.chat.domain.entity.RoomGroup;

import java.util.List;

/**
 * 房间底层管理
 * @author nyh
 */
public interface RoomService {

    /**
     * 创建一个单聊房间
     */
    RoomFriend createFriendRoom(List<Long> uidList);

    RoomFriend getFriendRoom(Long uid1, Long uid2);

    /**
     * 禁用一个单聊房间
     */
    void disableFriendRoom(List<Long> uidList);


    /**
     * 创建一个群聊房间
     */
    RoomGroup createGroupRoom(Long uid, String groupName);


    List<RoomChatInfoPO> chatInfo(Long uid, List<Long> roomIds, int type);

    List<GroupListVO> groupList(Long uid);

}
