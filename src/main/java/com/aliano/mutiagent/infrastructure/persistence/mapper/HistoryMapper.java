package com.aliano.mutiagent.infrastructure.persistence.mapper;

import com.aliano.mutiagent.application.dto.HistorySearchMessageHit;
import com.aliano.mutiagent.application.dto.HistorySearchSessionHit;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface HistoryMapper {

    @Insert("""
            INSERT INTO message_fts (
                message_id, session_id, role, message_type, content_text, raw_chunk
            )
            SELECT
                m.id,
                m.session_id,
                m.role,
                m.message_type,
                COALESCE(m.content_text, ''),
                COALESCE(m.raw_chunk, '')
            FROM message m
            WHERE NOT EXISTS (
                SELECT 1
                FROM message_fts f
                WHERE f.message_id = m.id
            )
            """)
    void syncMessageFts();

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM session s",
            "JOIN app_instance ai ON ai.id = s.app_instance_id",
            "WHERE 1 = 1",
            "<if test='appType != null and appType != \"\"'> AND ai.app_type = #{appType}</if>",
            "<if test='projectPath != null and projectPath != \"\"'>",
            "  AND COALESCE(s.project_path, '') LIKE '%' || #{projectPath} || '%'",
            "</if>",
            "<if test='dateFrom != null and dateFrom != \"\"'> AND date(s.created_at) &gt;= date(#{dateFrom})</if>",
            "<if test='dateTo != null and dateTo != \"\"'> AND date(s.created_at) &lt;= date(#{dateTo})</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "      s.title LIKE '%' || #{keyword} || '%'",
            "      OR COALESCE(s.project_path, '') LIKE '%' || #{keyword} || '%'",
            "      OR COALESCE(s.summary, '') LIKE '%' || #{keyword} || '%'",
            "      OR EXISTS (",
            "          SELECT 1",
            "          FROM message m1",
            "          WHERE m1.session_id = s.id",
            "            AND (",
            "              COALESCE(m1.content_text, '') LIKE '%' || #{keyword} || '%'",
            "              OR COALESCE(m1.raw_chunk, '') LIKE '%' || #{keyword} || '%'",
            "            )",
            "      )",
            "  )",
            "</if>",
            "</script>"
    })
    long countSessionHits(@Param("keyword") String keyword,
                          @Param("appType") String appType,
                          @Param("projectPath") String projectPath,
                          @Param("dateFrom") String dateFrom,
                          @Param("dateTo") String dateTo);

    @Select({
            "<script>",
            "SELECT",
            "  s.id AS sessionId,",
            "  s.title AS title,",
            "  s.app_instance_id AS appInstanceId,",
            "  ai.app_type AS appType,",
            "  ai.name AS instanceName,",
            "  s.project_path AS projectPath,",
            "  s.status AS status,",
            "  s.interaction_mode AS interactionMode,",
            "  s.created_at AS createdAt,",
            "  s.last_message_at AS lastMessageAt,",
            "  s.summary AS summary,",
            "  (",
            "      SELECT substr(COALESCE(m.content_text, m.raw_chunk, ''), 1, 200)",
            "      FROM message m",
            "      WHERE m.session_id = s.id",
            "      <if test='keyword != null and keyword != \"\"'>",
            "        AND (",
            "          COALESCE(m.content_text, '') LIKE '%' || #{keyword} || '%'",
            "          OR COALESCE(m.raw_chunk, '') LIKE '%' || #{keyword} || '%'",
            "        )",
            "      </if>",
            "      ORDER BY m.created_at DESC",
            "      LIMIT 1",
            "  ) AS matchedMessageText",
            "FROM session s",
            "JOIN app_instance ai ON ai.id = s.app_instance_id",
            "WHERE 1 = 1",
            "<if test='appType != null and appType != \"\"'> AND ai.app_type = #{appType}</if>",
            "<if test='projectPath != null and projectPath != \"\"'>",
            "  AND COALESCE(s.project_path, '') LIKE '%' || #{projectPath} || '%'",
            "</if>",
            "<if test='dateFrom != null and dateFrom != \"\"'> AND date(s.created_at) &gt;= date(#{dateFrom})</if>",
            "<if test='dateTo != null and dateTo != \"\"'> AND date(s.created_at) &lt;= date(#{dateTo})</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "      s.title LIKE '%' || #{keyword} || '%'",
            "      OR COALESCE(s.project_path, '') LIKE '%' || #{keyword} || '%'",
            "      OR COALESCE(s.summary, '') LIKE '%' || #{keyword} || '%'",
            "      OR EXISTS (",
            "          SELECT 1",
            "          FROM message m1",
            "          WHERE m1.session_id = s.id",
            "            AND (",
            "              COALESCE(m1.content_text, '') LIKE '%' || #{keyword} || '%'",
            "              OR COALESCE(m1.raw_chunk, '') LIKE '%' || #{keyword} || '%'",
            "            )",
            "      )",
            "  )",
            "</if>",
            "ORDER BY ${orderBy}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<HistorySearchSessionHit> searchSessionHits(@Param("keyword") String keyword,
                                                    @Param("appType") String appType,
                                                    @Param("projectPath") String projectPath,
                                                    @Param("dateFrom") String dateFrom,
                                                    @Param("dateTo") String dateTo,
                                                    @Param("orderBy") String orderBy,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM message_fts",
            "JOIN message m ON m.id = message_fts.message_id",
            "JOIN session s ON s.id = m.session_id",
            "JOIN app_instance ai ON ai.id = s.app_instance_id",
            "WHERE message_fts MATCH #{ftsKeyword}",
            "<if test='appType != null and appType != \"\"'> AND ai.app_type = #{appType}</if>",
            "<if test='projectPath != null and projectPath != \"\"'>",
            "  AND COALESCE(s.project_path, '') LIKE '%' || #{projectPath} || '%'",
            "</if>",
            "<if test='dateFrom != null and dateFrom != \"\"'> AND date(m.created_at) &gt;= date(#{dateFrom})</if>",
            "<if test='dateTo != null and dateTo != \"\"'> AND date(m.created_at) &lt;= date(#{dateTo})</if>",
            "</script>"
    })
    long countMessageHitsByFts(@Param("ftsKeyword") String ftsKeyword,
                               @Param("appType") String appType,
                               @Param("projectPath") String projectPath,
                               @Param("dateFrom") String dateFrom,
                               @Param("dateTo") String dateTo);

    @Select({
            "<script>",
            "SELECT",
            "  m.id AS messageId,",
            "  m.session_id AS sessionId,",
            "  s.title AS sessionTitle,",
            "  s.app_instance_id AS appInstanceId,",
            "  ai.app_type AS appType,",
            "  ai.name AS instanceName,",
            "  s.project_path AS projectPath,",
            "  m.seq_no AS seqNo,",
            "  m.role AS role,",
            "  m.message_type AS messageType,",
            "  COALESCE(",
            "      NULLIF(snippet(message_fts, 4, '', '', '...', 12), ''),",
            "      substr(COALESCE(m.content_text, m.raw_chunk, ''), 1, 200)",
            "  ) AS snippet,",
            "  m.created_at AS createdAt",
            "FROM message_fts",
            "JOIN message m ON m.id = message_fts.message_id",
            "JOIN session s ON s.id = m.session_id",
            "JOIN app_instance ai ON ai.id = s.app_instance_id",
            "WHERE message_fts MATCH #{ftsKeyword}",
            "<if test='appType != null and appType != \"\"'> AND ai.app_type = #{appType}</if>",
            "<if test='projectPath != null and projectPath != \"\"'>",
            "  AND COALESCE(s.project_path, '') LIKE '%' || #{projectPath} || '%'",
            "</if>",
            "<if test='dateFrom != null and dateFrom != \"\"'> AND date(m.created_at) &gt;= date(#{dateFrom})</if>",
            "<if test='dateTo != null and dateTo != \"\"'> AND date(m.created_at) &lt;= date(#{dateTo})</if>",
            "ORDER BY ${orderBy}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<HistorySearchMessageHit> searchMessageHitsByFts(@Param("ftsKeyword") String ftsKeyword,
                                                         @Param("appType") String appType,
                                                         @Param("projectPath") String projectPath,
                                                         @Param("dateFrom") String dateFrom,
                                                         @Param("dateTo") String dateTo,
                                                         @Param("orderBy") String orderBy,
                                                         @Param("limit") int limit,
                                                         @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM message m",
            "JOIN session s ON s.id = m.session_id",
            "JOIN app_instance ai ON ai.id = s.app_instance_id",
            "WHERE (",
            "  COALESCE(m.content_text, '') LIKE '%' || #{keyword} || '%'",
            "  OR COALESCE(m.raw_chunk, '') LIKE '%' || #{keyword} || '%'",
            ")",
            "<if test='appType != null and appType != \"\"'> AND ai.app_type = #{appType}</if>",
            "<if test='projectPath != null and projectPath != \"\"'>",
            "  AND COALESCE(s.project_path, '') LIKE '%' || #{projectPath} || '%'",
            "</if>",
            "<if test='dateFrom != null and dateFrom != \"\"'> AND date(m.created_at) &gt;= date(#{dateFrom})</if>",
            "<if test='dateTo != null and dateTo != \"\"'> AND date(m.created_at) &lt;= date(#{dateTo})</if>",
            "</script>"
    })
    long countMessageHitsByLike(@Param("keyword") String keyword,
                                @Param("appType") String appType,
                                @Param("projectPath") String projectPath,
                                @Param("dateFrom") String dateFrom,
                                @Param("dateTo") String dateTo);

    @Select({
            "<script>",
            "SELECT",
            "  m.id AS messageId,",
            "  m.session_id AS sessionId,",
            "  s.title AS sessionTitle,",
            "  s.app_instance_id AS appInstanceId,",
            "  ai.app_type AS appType,",
            "  ai.name AS instanceName,",
            "  s.project_path AS projectPath,",
            "  m.seq_no AS seqNo,",
            "  m.role AS role,",
            "  m.message_type AS messageType,",
            "  substr(COALESCE(m.content_text, m.raw_chunk, ''), 1, 200) AS snippet,",
            "  m.created_at AS createdAt",
            "FROM message m",
            "JOIN session s ON s.id = m.session_id",
            "JOIN app_instance ai ON ai.id = s.app_instance_id",
            "WHERE (",
            "  COALESCE(m.content_text, '') LIKE '%' || #{keyword} || '%'",
            "  OR COALESCE(m.raw_chunk, '') LIKE '%' || #{keyword} || '%'",
            ")",
            "<if test='appType != null and appType != \"\"'> AND ai.app_type = #{appType}</if>",
            "<if test='projectPath != null and projectPath != \"\"'>",
            "  AND COALESCE(s.project_path, '') LIKE '%' || #{projectPath} || '%'",
            "</if>",
            "<if test='dateFrom != null and dateFrom != \"\"'> AND date(m.created_at) &gt;= date(#{dateFrom})</if>",
            "<if test='dateTo != null and dateTo != \"\"'> AND date(m.created_at) &lt;= date(#{dateTo})</if>",
            "ORDER BY ${orderBy}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<HistorySearchMessageHit> searchMessageHitsByLike(@Param("keyword") String keyword,
                                                          @Param("appType") String appType,
                                                          @Param("projectPath") String projectPath,
                                                          @Param("dateFrom") String dateFrom,
                                                          @Param("dateTo") String dateTo,
                                                          @Param("orderBy") String orderBy,
                                                          @Param("limit") int limit,
                                                          @Param("offset") int offset);
}
