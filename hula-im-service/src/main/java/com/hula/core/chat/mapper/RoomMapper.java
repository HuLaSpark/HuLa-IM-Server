package com.hula.core.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hula.common.domain.po.RoomChatInfoPO;
import com.hula.common.domain.vo.res.GroupListVO;
import com.hula.core.chat.domain.entity.Room;

import java.util.List;

/**
 * <p>
 * 房间表 Mapper 接口
 * </p>
 *
 * @author nyh
 */
public interface RoomMapper extends BaseMapper<Room> {

    List<RoomChatInfoPO> chatInfo(Long uid, List<Long> roomIds, int type);

    List<GroupListVO> groupList(Long uid);
}
