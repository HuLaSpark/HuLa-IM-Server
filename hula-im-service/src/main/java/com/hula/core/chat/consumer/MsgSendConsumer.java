package com.hula.core.chat.consumer;

import com.hula.common.constant.MqConstant;
import com.hula.common.domain.dto.MsgSendMessageDTO;
import com.hula.core.chat.dao.ContactDao;
import com.hula.core.chat.dao.MessageDao;
import com.hula.core.chat.dao.RoomDao;
import com.hula.core.chat.dao.RoomFriendDao;
import com.hula.core.chat.domain.entity.Message;
import com.hula.core.chat.domain.entity.Room;
import com.hula.core.chat.domain.entity.RoomFriend;
import com.hula.core.chat.domain.enums.RoomTypeEnum;
import com.hula.core.chat.domain.vo.response.ChatMessageResp;
import com.hula.core.chat.service.ChatService;
import com.hula.core.chat.service.WeChatMsgOperationService;
import com.hula.core.chat.service.cache.GroupMemberCache;
import com.hula.core.chat.service.cache.HotRoomCache;
import com.hula.core.chat.service.cache.RoomCache;
import com.hula.core.user.service.adapter.WsAdapter;
import com.hula.core.user.service.impl.PushService;
import lombok.AllArgsConstructor;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 发送消息更新房间收信箱，并同步给房间成员信箱
 * @author nyh
 */
@RocketMQMessageListener(consumerGroup = MqConstant.SEND_MSG_GROUP, topic = MqConstant.SEND_MSG_TOPIC, messageModel = MessageModel.BROADCASTING)
@Component
@AllArgsConstructor
public class MsgSendConsumer implements RocketMQListener<MsgSendMessageDTO> {

    private ChatService chatService;
    private MessageDao messageDao;
    private WeChatMsgOperationService weChatMsgOperationService;
    private RoomCache roomCache;
    private RoomDao roomDao;
    private GroupMemberCache groupMemberCache;
    private RoomFriendDao roomFriendDao;
    private ContactDao contactDao;
    private HotRoomCache hotRoomCache;
    private PushService pushService;

    @Override
    public void onMessage(MsgSendMessageDTO dto) {
        Message message = messageDao.getById(dto.getMsgId());
        if (Objects.isNull(message)) {
            return;
        }
        Room room = roomCache.get(message.getRoomId());
        ChatMessageResp msgResp = chatService.getMsgResp(message, null);
        // 所有房间更新房间最新消息
        roomDao.refreshActiveTime(room.getId(), message.getId(), message.getCreateTime());
        roomCache.delete(room.getId());
        if (room.isHotRoom()) {
            //热门群聊推送所有在线的人
            //更新热门群聊时间-redis
            hotRoomCache.refreshActiveTime(room.getId(), message.getCreateTime());
            //推送所有人
            pushService.sendPushMsg(WsAdapter.buildMsgSend(msgResp), dto.getUid());
        } else {
            List<Long> memberUidList = new ArrayList<>();
            if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {
                // 普通群聊推送所有群成员，过滤掉当前用户
                memberUidList = groupMemberCache.getMemberUidList(room.getId()).stream().filter(uid->!dto.getUid().equals(uid)).toList();
            } else if (Objects.equals(room.getType(), RoomTypeEnum.FRIEND.getType())) {
                // 单聊对象
                // 对单人推送
                RoomFriend roomFriend = roomFriendDao.getByRoomId(room.getId());
                // 不对自己发送消息
                memberUidList = Stream.of(roomFriend.getUid1(), roomFriend.getUid2()).filter(uid->!dto.getUid().equals(uid)).toList();
            }
            // 更新所有群成员的会话时间
            contactDao.refreshOrCreateActiveTime(room.getId(), memberUidList, message.getId(), message.getCreateTime());
            // 推送房间成员
            pushService.sendPushMsg(WsAdapter.buildMsgSend(msgResp), memberUidList, dto.getUid());
        }
    }


}
