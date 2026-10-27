package com.hospital.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.message.entity.SysMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SysMessageMapper extends BaseMapper<SysMessage> {

    @Select("""
            SELECT COUNT(1) FROM sys_message
             WHERE receiver_id = #{receiverId} AND is_read = 0
            """)
    int countUnread(@Param("receiverId") Long receiverId);

    /**
     * 标记已读。
     *
     * <p><b>必须带 {@code receiver_id}</b>：只按 id 更新的话，
     * 改一下 URL 里的 id 就能把别人的消息标记为已读 —— 典型的越权。
     * 这条 SQL 让越权请求自然返回 0 行，落到 40900。</p>
     */
    @Update("""
            UPDATE sys_message
               SET is_read = 1
             WHERE id = #{id}
               AND receiver_id = #{receiverId}
               AND is_read = 0
            """)
    int markRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    @Update("""
            UPDATE sys_message
               SET is_read = 1
             WHERE receiver_id = #{receiverId}
               AND is_read = 0
            """)
    int markAllRead(@Param("receiverId") Long receiverId);
}
