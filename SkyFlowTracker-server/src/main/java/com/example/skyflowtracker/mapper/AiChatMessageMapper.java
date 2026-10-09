package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.AiChatMessage;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiChatMessageMapper {

    @Insert("INSERT INTO ai_chat_messages (user_id, conversation_id, role, content, create_time) " +
            "VALUES (#{userId}, #{conversationId}, #{role}, #{content}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiChatMessage message);

    @Select("SELECT * FROM ai_chat_messages WHERE conversation_id = #{conversationId} " +
            "ORDER BY create_time ASC")
    List<AiChatMessage> selectByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT conversation_id, MAX(content) AS last_message, MAX(create_time) AS last_time " +
            "FROM ai_chat_messages WHERE user_id = #{userId} AND role = 'user' " +
            "GROUP BY conversation_id ORDER BY last_time DESC")
    List<Map<String, Object>> selectConversationsByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM ai_chat_messages WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    int deleteConversation(@Param("conversationId") String conversationId, @Param("userId") String userId);
}
