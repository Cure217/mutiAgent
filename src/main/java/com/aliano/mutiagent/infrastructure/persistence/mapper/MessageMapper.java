package com.aliano.mutiagent.infrastructure.persistence.mapper;

import com.aliano.mutiagent.domain.message.MessageRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MessageMapper {

    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
            ORDER BY seq_no ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MessageRecord> findPage(@Param("sessionId") String sessionId,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    @Select("SELECT COUNT(1) FROM message WHERE session_id = #{sessionId}")
    long countBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT COUNT(1) FROM message")
    long countAll();

    @Select("SELECT COALESCE(MAX(seq_no), 0) + 1 FROM message WHERE session_id = #{sessionId}")
    int nextSeqNo(@Param("sessionId") String sessionId);

    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
            ORDER BY seq_no DESC
            LIMIT #{limit}
            """)
    List<MessageRecord> findLatestForTimeline(@Param("sessionId") String sessionId,
                                              @Param("limit") int limit);

    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
              AND seq_no BETWEEN
                  ((SELECT seq_no FROM message WHERE id = #{messageId} AND session_id = #{sessionId}) - #{before})
              AND
                  ((SELECT seq_no FROM message WHERE id = #{messageId} AND session_id = #{sessionId}) + #{after})
            ORDER BY seq_no ASC
            """)
    List<MessageRecord> findWindowByMessageId(@Param("sessionId") String sessionId,
                                              @Param("messageId") String messageId,
                                              @Param("before") int before,
                                              @Param("after") int after);

    @Select("""
            SELECT * FROM message
            WHERE session_id = #{sessionId}
              AND id = #{messageId}
            LIMIT 1
            """)
    MessageRecord findById(@Param("sessionId") String sessionId,
                           @Param("messageId") String messageId);

    @Insert("""
            INSERT INTO message (
                id, session_id, seq_no, role, message_type, content_text, content_json,
                raw_chunk, parent_id, is_structured, source_adapter, created_at
            ) VALUES (
                #{id}, #{sessionId}, #{seqNo}, #{role}, #{messageType}, #{contentText}, #{contentJson},
                #{rawChunk}, #{parentId}, #{isStructured}, #{sourceAdapter}, #{createdAt}
            )
            """)
    void insert(MessageRecord messageRecord);

    @Insert("""
            INSERT INTO message_fts (
                message_id, session_id, role, message_type, content_text, raw_chunk
            )
            SELECT
                id,
                session_id,
                role,
                message_type,
                COALESCE(content_text, ''),
                COALESCE(raw_chunk, '')
            FROM message
            WHERE id = #{messageId}
              AND NOT EXISTS (
                  SELECT 1
                  FROM message_fts
                  WHERE message_id = #{messageId}
              )
            """)
    void syncFtsByMessageId(@Param("messageId") String messageId);
}
