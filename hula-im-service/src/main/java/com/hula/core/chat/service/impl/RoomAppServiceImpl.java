package com.hula.core.chat.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.hula.common.annotation.RedissonLock;
import com.hula.common.domain.po.RoomChatInfoPO;
import com.hula.common.domain.vo.req.CursorPageBaseReq;
import com.hula.common.domain.vo.res.CursorPageBaseResp;
import com.hula.common.domain.vo.res.GroupListVO;
import com.hula.common.event.GroupMemberAddEvent;
import com.hula.core.chat.dao.ContactDao;
import com.hula.core.chat.dao.GroupMemberDao;
import com.hula.core.chat.dao.MessageDao;
import com.hula.core.chat.domain.dto.RoomBaseInfo;
import com.hula.core.chat.domain.entity.*;
import com.hula.core.chat.domain.enums.GroupRoleAPPEnum;
import com.hula.core.chat.domain.enums.GroupRoleEnum;
import com.hula.core.chat.domain.enums.HotFlagEnum;
import com.hula.core.chat.domain.enums.RoomTypeEnum;
import com.hula.core.chat.domain.vo.request.ChatMessageMemberReq;
import com.hula.core.chat.domain.vo.request.ChatMessageReq;
import com.hula.core.chat.domain.vo.request.GroupAddReq;
import com.hula.core.chat.domain.vo.request.member.MemberAddReq;
import com.hula.core.chat.domain.vo.request.member.MemberDelReq;
import com.hula.core.chat.domain.vo.request.member.MemberReq;
import com.hula.core.chat.domain.vo.response.ChatMemberListResp;
import com.hula.core.chat.domain.vo.response.ChatRoomResp;
import com.hula.core.chat.domain.vo.response.MemberResp;
import com.hula.core.chat.service.ChatService;
import com.hula.core.chat.service.RoomAppService;
import com.hula.core.chat.service.RoomService;
import com.hula.core.chat.service.adapter.ChatAdapter;
import com.hula.core.chat.service.adapter.MemberAdapter;
import com.hula.core.chat.service.adapter.RoomAdapter;
import com.hula.core.chat.service.cache.*;
import com.hula.core.chat.service.strategy.msg.AbstractMsgHandler;
import com.hula.core.chat.service.strategy.msg.MsgHandlerFactory;
import com.hula.core.user.dao.UserDao;
import com.hula.core.user.domain.entity.User;
import com.hula.core.user.domain.enums.RoleTypeEnum;
import com.hula.core.user.domain.enums.WsBaseResp;
import com.hula.core.user.domain.vo.resp.ws.ChatMemberResp;
import com.hula.core.user.domain.vo.resp.ws.WSMemberChange;
import com.hula.core.user.service.RoleService;
import com.hula.core.user.service.cache.UserCache;
import com.hula.core.user.service.cache.UserInfoCache;
import com.hula.core.user.service.impl.PushService;
import com.hula.enums.GroupErrorEnum;
import com.hula.utils.AssertUtil;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@AllArgsConstructor
public class RoomAppServiceImpl implements RoomAppService {
    
    private ContactDao contactDao;
    private RoomCache roomCache;
    private RoomGroupCache roomGroupCache;
    private RoomFriendCache roomFriendCache;
    private UserInfoCache userInfoCache;
    private MessageDao messageDao;
    private HotRoomCache hotRoomCache;
    private UserCache userCache;
    private GroupMemberDao groupMemberDao;
    private UserDao userDao;
    private ChatService chatService;
    private RoleService roleService;
    private ApplicationEventPublisher applicationEventPublisher;
    private RoomService roomService;
    private GroupMemberCache groupMemberCache;
    private PushService pushService;
    private TransactionTemplate transactionTemplate;
    @Override
    public CursorPageBaseResp<ChatRoomResp> getContactPage(CursorPageBaseReq request, Long uid) {
        // 查出用户要展示的会话列表
        CursorPageBaseResp<Long> page;
        if (Objects.nonNull(uid)) {
            Double hotEnd = getCursorOrNull(request.getCursor());
            Double hotStart = null;
            // 用户基础会话
            CursorPageBaseResp<Contact> contactPage = contactDao.getContactPage(uid, request);
            List<Long> baseRoomIds = contactPage.getList().stream().map(Contact::getRoomId).collect(Collectors.toList());
            if (!contactPage.getIsLast()) {
                hotStart = getCursorOrNull(contactPage.getCursor());
            }
            // 热门房间
            Set<ZSetOperations.TypedTuple<String>> typedTuples = hotRoomCache.getRoomRange(hotStart, hotEnd);
            List<Long> hotRoomIds = typedTuples.stream().map(ZSetOperations.TypedTuple::getValue).filter(Objects::nonNull).map(Long::parseLong).collect(Collectors.toList());
            baseRoomIds.addAll(hotRoomIds);
            // 基础会话和热门房间合并
            page = CursorPageBaseResp.init(contactPage, baseRoomIds, 0L);
        } else {// 用户未登录，只查全局房间
            CursorPageBaseResp<Pair<Long, Double>> roomCursorPage = hotRoomCache.getRoomCursorPage(request);
            List<Long> roomIds = roomCursorPage.getList().stream().map(Pair::getKey).collect(Collectors.toList());
            page = CursorPageBaseResp.init(roomCursorPage, roomIds,0L);
        }
        // 最后组装会话信息（名称，头像，未读数等）
        List<ChatRoomResp> result = buildContactResp(uid, page.getList());
        return CursorPageBaseResp.init(page, result,0L);
    }

    @Override
    public ChatRoomResp getContactDetail(Long uid, Long roomId) {
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(room, "房间号有误");
        return buildContactResp(uid, Collections.singletonList(roomId)).get(0);
    }

    @Override
    public ChatRoomResp getContactDetailByFriend(Long uid, Long friendUid) {
        RoomFriend friendRoom = roomService.getFriendRoom(uid, friendUid);
        AssertUtil.isNotEmpty(friendRoom, "他不是您的好友");
        return buildContactResp(uid, Collections.singletonList(friendRoom.getRoomId())).getFirst();
    }

    @Override
    public List<GroupListVO> groupList(Long uid) {
        return roomService.groupList(uid);
    }

    @Override
    public MemberResp getGroupDetail(Long uid, long roomId) {
        RoomGroup roomGroup = roomGroupCache.get(roomId);
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(roomGroup, "roomId有误");
        Long onlineNum;
        if (isHotGroup(room)) {
            // 热点群从redis取人数
            onlineNum = userCache.getOnlineNum();
        } else {
            List<Long> memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
            onlineNum = userDao.getOnlineCount(memberUidList).longValue();
        }
        GroupRoleAPPEnum groupRole = getGroupRole(uid, roomGroup, room);
        return MemberResp.builder()
                .avatar(roomGroup.getAvatar())
                .roomId(roomId)
                .groupName(roomGroup.getName())
                .onlineNum(onlineNum)
                .role(groupRole.getType())
                .build();
    }

    @Override
    public CursorPageBaseResp<ChatMemberResp> getMemberPage(MemberReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        List<Long> memberUidList;
        if (isHotGroup(room)) {
            // 全员群展示所有用户
            memberUidList = null;
        } else {// 只展示房间内的群成员
            RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
            memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
        }
        return chatService.getMemberPage(memberUidList, request);
    }

    @Override
    @Cacheable(cacheNames = "member", key = "'memberList.'+#request.roomId")
    public List<ChatMemberListResp> getMemberList(ChatMessageMemberReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        if (isHotGroup(room)) {
            // 全员群展示所有用户100名
            List<User> memberList = userDao.getMemberList();
            return MemberAdapter.buildMemberList(memberList);
        } else {
            RoomGroup roomGroup = roomGroupCache.get(room.getId());
            List<Long> memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
            Map<Long, User> batch = userInfoCache.getBatch(memberUidList);
            return MemberAdapter.buildMemberList(batch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delMember(Long uid, MemberDelReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, GroupErrorEnum.USER_NOT_IN_GROUP, "groupMember");
        // 1. 判断被移除的人是否是群主或者管理员  （群主不可以被移除，管理员只能被群主移除）
        Long removedUid = request.getUid();
        // 1.1 群主 非法操作
        AssertUtil.isFalse(groupMemberDao.isLord(roomGroup.getId(), removedUid), GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE, "");
        // 1.2 管理员 判断是否是群主操作
        if (groupMemberDao.isManager(roomGroup.getId(), removedUid)) {
            Boolean isLord = groupMemberDao.isLord(roomGroup.getId(), uid);
            AssertUtil.isTrue(isLord, GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        }
        // 1.3 普通成员 判断是否有权限操作
        AssertUtil.isTrue(hasPower(self), GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        GroupMember member = groupMemberDao.getMember(roomGroup.getId(), removedUid);
        AssertUtil.isNotEmpty(member, "用户已经移除");
        groupMemberDao.removeById(member.getId());
        // 发送移除事件告知群成员
        List<Long> memberUidList = groupMemberCache.getMemberUidList(roomGroup.getRoomId());
        WsBaseResp<WSMemberChange> ws = MemberAdapter.buildMemberRemoveWS(roomGroup.getRoomId(), member.getUid());
        pushService.sendPushMsg(ws, memberUidList, uid);
        groupMemberCache.evictMemberUidList(room.getId());
    }


    @Override
    @RedissonLock(key = "#request.roomId")
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long uid, MemberAddReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        AssertUtil.isFalse(isHotGroup(room), "全员群无需邀请好友");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, "您不是群成员");
        List<Long> memberBatch = groupMemberDao.getMemberBatch(roomGroup.getId(), request.getUidList());
        Set<Long> existUid = new HashSet<>(memberBatch);
        List<Long> waitAddUidList = request.getUidList().stream().filter(a -> !existUid.contains(a)).distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(waitAddUidList)) {
            return;
        }
        List<GroupMember> groupMembers = MemberAdapter.buildMemberAdd(roomGroup.getId(), waitAddUidList);
        groupMemberDao.saveBatch(groupMembers);

        User user = userInfoCache.get(uid);
        List<Long> uidList = groupMembers.stream().map(GroupMember::getUid).collect(Collectors.toList());
        ChatMessageReq chatMessageReq = RoomAdapter.buildGroupAddMessage(roomGroup, user, userInfoCache.getBatch(uidList));
        chatService.sendMsg(chatMessageReq, User.UID_SYSTEM);

        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup, groupMembers, uid));
    }

    @Override
    public Long addGroup(Long uid, GroupAddReq request) {
        final RoomGroup[] roomGroup = new RoomGroup[1];
        List<GroupMember> groupMembers = transactionTemplate.execute((status -> {
            try {
                roomGroup[0] = roomService.createGroupRoom(uid,request.getGroupName());
                // 批量保存群成员
                List<GroupMember> members = RoomAdapter.buildGroupMemberBatch(request.getUidList(), roomGroup[0].getId());
                groupMemberDao.saveBatch(members);
                return members;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        }));
        User user = userInfoCache.get(uid);
        List<Long> uidList = groupMembers.stream().map(GroupMember::getUid).collect(Collectors.toList());
        ChatMessageReq chatMessageReq = RoomAdapter.buildGroupAddMessage(roomGroup[0], user, userInfoCache.getBatch(uidList));
        chatService.sendMsg(chatMessageReq, uid);

        // 发送邀请加群消息==》触发每个人的会话
        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup[0], groupMembers, uid));
        return roomGroup[0].getRoomId();
    }

    private boolean hasPower(GroupMember self) {
        return Objects.equals(self.getRole(), GroupRoleEnum.LEADER.getType())
                || Objects.equals(self.getRole(), GroupRoleEnum.MANAGER.getType())
                || roleService.hasRole(self.getUid(), RoleTypeEnum.ADMIN);
    }

    private GroupRoleAPPEnum getGroupRole(Long uid, RoomGroup roomGroup, Room room) {
        GroupMember member = Objects.isNull(uid) ? null : groupMemberDao.getMember(roomGroup.getId(), uid);
        if (Objects.nonNull(member)) {
            return GroupRoleAPPEnum.of(member.getRole());
        } else if (isHotGroup(room)) {
            return GroupRoleAPPEnum.MEMBER;
        } else {
            return GroupRoleAPPEnum.REMOVE;
        }
    }

    private boolean isHotGroup(Room room) {
        return HotFlagEnum.YES.getType().equals(room.getHotFlag());
    }

    private List<Contact> buildContact(List<Pair<Long, Double>> list, Long uid) {
        List<Long> roomIds = list.stream().map(Pair::getKey).collect(Collectors.toList());
        Map<Long, Room> batch = roomCache.getBatch(roomIds);
        Map<Long, Contact> contactMap = new HashMap<>();
        if (Objects.nonNull(uid)) {
            List<Contact> byRoomIds = contactDao.getByRoomIds(roomIds, uid);
            contactMap = byRoomIds.stream().collect(Collectors.toMap(Contact::getRoomId, Function.identity()));
        }
        Map<Long, Contact> finalContactMap = contactMap;
        return list.stream().map(pair -> {
            Long roomId = pair.getKey();
            Contact contact = finalContactMap.get(roomId);
            if (Objects.isNull(contact)) {
                contact = new Contact();
                contact.setRoomId(pair.getKey());
                Room room = batch.get(roomId);
                contact.setLastMsgId(room.getLastMsgId());
            }
            contact.setActiveTime(new Date(pair.getValue().longValue()));
            return contact;
        }).collect(Collectors.toList());
    }

    private Double getCursorOrNull(String cursor) {
        if (StringUtils.isEmpty(cursor)) {
            return null;
        }
        return Optional.of(cursor).map(Double::parseDouble).orElse(null);
    }

    @NotNull
    private List<ChatRoomResp> buildContactResp(Long uid, List<Long> roomIds) {
        // 表情和头像
        Map<Long, RoomBaseInfo> roomBaseInfoMap = getRoomBaseInfoMap(roomIds, uid);
        Map<Long,Long> chatMap = getChat(uid,roomIds);
        // 最后一条消息
        List<Long> msgIds = roomBaseInfoMap.values().stream().map(RoomBaseInfo::getLastMsgId).collect(Collectors.toList());
        List<Message> messages = CollectionUtil.isEmpty(msgIds) ? new ArrayList<>() : messageDao.listByIds(msgIds);
        Map<Long, Message> msgMap = messages.stream().collect(Collectors.toMap(Message::getId, Function.identity()));
        Map<Long, User> lastMsgUidMap = userInfoCache.getBatch(messages.stream().map(Message::getFromUid).collect(Collectors.toList()));
        // 消息未读数
        Map<Long, Integer> unReadCountMap = getUnReadCountMap(uid, roomIds);
        return roomBaseInfoMap.values().stream().map(room -> {
                    ChatRoomResp resp = new ChatRoomResp();
                    Long roomId = room.getRoomId();
                    RoomBaseInfo roomBaseInfo = roomBaseInfoMap.get(roomId);
                    Long friendId = chatMap.get(roomId);
                    if (Objects.nonNull(friendId)&&Objects.equals(room.getType(),RoomTypeEnum.FRIEND.getType())){
                        resp.setFriendId(friendId);
                    }
                    resp.setAvatar(roomBaseInfo.getAvatar());
                    resp.setRoomId(roomId);
                    resp.setActiveTime(room.getActiveTime());
                    resp.setHotFlag(roomBaseInfo.getHotFlag());
                    resp.setType(roomBaseInfo.getType());
                    resp.setName(roomBaseInfo.getName());
                    Message message = msgMap.get(room.getLastMsgId());
                    if (Objects.nonNull(message)) {
                        AbstractMsgHandler strategyNoNull = MsgHandlerFactory.getStrategyNoNull(message.getType());
                        // 判断是群聊还是单聊
                        if (Objects.equals(roomBaseInfo.getType(), RoomTypeEnum.GROUP.getType())) {
                            resp.setText(lastMsgUidMap.get(message.getFromUid()).getName() + ":" + strategyNoNull.showContactMsg(message));
                        } else {
                            resp.setText(strategyNoNull.showContactMsg(message));
                        }
                    }
                    resp.setUnreadCount(unReadCountMap.getOrDefault(roomId, 0));
                    return resp;
                }).sorted(Comparator.comparing(ChatRoomResp::getActiveTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取单聊的对象map
     * key->roomId
     * v->单聊对象id
     * @param uid     用户id
     * @param roomIds 房间 ID
     * @return {@link Map }<{@link Long }, {@link Long }>
     */
    private Map<Long, Long> getChat(Long uid, List<Long> roomIds) {
        List<RoomChatInfoPO> poList = roomService.chatInfo(uid, roomIds, RoomTypeEnum.FRIEND.getType());
        Map<Long, Long> map = new HashMap<>();
        poList.forEach(e -> {
            Long friendId;
            if (Objects.equals(uid, e.getUid())) {
                friendId = e.getFriendId();
            } else {
                friendId = e.getUid();
            }
            map.put(e.getRoomId(), friendId);
        });
        return map;
    }

    /**
     * 获取未读数
     */
    private Map<Long, Integer> getUnReadCountMap(Long uid, List<Long> roomIds) {
        if (Objects.isNull(uid)) {
            return new HashMap<>();
        }
        List<Contact> contacts = contactDao.getByRoomIds(roomIds, uid);
        return contacts.parallelStream()
                .map(contact -> Pair.of(contact.getRoomId(), messageDao.getUnReadCount(contact.getRoomId(), contact.getReadTime())))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Map<Long, User> getFriendRoomMap(List<Long> roomIds, Long uid) {
        if (CollectionUtil.isEmpty(roomIds)) {
            return new HashMap<>();
        }
        Map<Long, RoomFriend> roomFriendMap = roomFriendCache.getBatch(roomIds);
        Set<Long> friendUidSet = ChatAdapter.getFriendUidSet(roomFriendMap.values(), uid);
        Map<Long, User> userBatch = userInfoCache.getBatch(new ArrayList<>(friendUidSet));
        return roomFriendMap.values()
                .stream()
                .collect(Collectors.toMap(RoomFriend::getRoomId, roomFriend -> {
                    Long friendUid = ChatAdapter.getFriendUid(roomFriend, uid);
                    return userBatch.get(friendUid);
                }));
    }

    private Map<Long, RoomBaseInfo> getRoomBaseInfoMap(List<Long> roomIds, Long uid) {
        Map<Long, Room> roomMap = roomCache.getBatch(roomIds);
        // 房间根据好友和群组类型分组
        Map<Integer, List<Long>> groupRoomIdMap = roomMap.values().stream().collect(Collectors.groupingBy(Room::getType,
                Collectors.mapping(Room::getId, Collectors.toList())));
        // 获取群组信息
        List<Long> groupRoomId = groupRoomIdMap.get(RoomTypeEnum.GROUP.getType());
        Map<Long, RoomGroup> roomInfoBatch = roomGroupCache.getBatch(groupRoomId);
        // 获取好友信息
        List<Long> friendRoomId = groupRoomIdMap.get(RoomTypeEnum.FRIEND.getType());
        Map<Long, User> friendRoomMap = getFriendRoomMap(friendRoomId, uid);

        return roomMap.values().stream().map(room -> {
            RoomBaseInfo roomBaseInfo = new RoomBaseInfo();
            roomBaseInfo.setRoomId(room.getId());
            roomBaseInfo.setType(room.getType());
            roomBaseInfo.setHotFlag(room.getHotFlag());
            roomBaseInfo.setLastMsgId(room.getLastMsgId());
            roomBaseInfo.setActiveTime(room.getActiveTime());
            if (RoomTypeEnum.of(room.getType()) == RoomTypeEnum.GROUP) {
                RoomGroup roomGroup = roomInfoBatch.get(room.getId());
                roomBaseInfo.setName(roomGroup.getName());
                roomBaseInfo.setAvatar(roomGroup.getAvatar());
            } else if (RoomTypeEnum.of(room.getType()) == RoomTypeEnum.FRIEND) {
                User user = friendRoomMap.get(room.getId());
                roomBaseInfo.setName(user.getName());
                roomBaseInfo.setAvatar(user.getAvatar());
            }
            return roomBaseInfo;
        }).collect(Collectors.toMap(RoomBaseInfo::getRoomId, Function.identity()));
    }

    private void fillRoomActive(Long uid, Map<Long, Room> roomMap) {
    }

}
