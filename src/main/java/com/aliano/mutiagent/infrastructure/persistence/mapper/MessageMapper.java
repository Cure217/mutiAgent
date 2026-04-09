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
}
