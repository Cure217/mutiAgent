package com.aliano.mutiagent.infrastructure.persistence.mapper;

import com.aliano.mutiagent.domain.session.AiSession;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SessionMapper {

    @Select({
            "<script>",
            "SELECT * FROM session",
            "WHERE 1 = 1",
            "<if test='appInstanceId != null and appInstanceId != \"\"'> AND app_instance_id = #{appInstanceId}</if>",
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (title LIKE '%' || #{keyword} || '%' OR project_path LIKE '%' || #{keyword} || '%')",
            "</if>",
            "ORDER BY created_at DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<AiSession> findPage(@Param("appInstanceId") String appInstanceId,
                             @Param("status") String status,
                             @Param("keyword") String keyword,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(1) FROM session",
            "WHERE 1 = 1",
            "<if test='appInstanceId != null and appInstanceId != \"\"'> AND app_instance_id = #{appInstanceId}</if>",
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (title LIKE '%' || #{keyword} || '%' OR project_path LIKE '%' || #{keyword} || '%')",
            "</if>",
            "</script>"
    })
    long countPage(@Param("appInstanceId") String appInstanceId,
                   @Param("status") String status,
                   @Param("keyword") String keyword);

    @Select("SELECT * FROM session WHERE id = #{id}")
    AiSession findById(@Param("id") String id);

    @Select("SELECT * FROM session WHERE status IN ('STARTING', 'RUNNING', 'STOPPING') ORDER BY created_at DESC")
    List<AiSession> findRunning();

    @Insert("""
            INSERT INTO session (
                id, app_instance_id, title, project_path, project_path_linux, status, interaction_mode,
                pid, started_at, ended_at, last_message_at, exit_code, exit_reason, raw_log_path,
                summary, tags_json, extra_json, created_at, updated_at
            ) VALUES (
                #{id}, #{appInstanceId}, #{title}, #{projectPath}, #{projectPathLinux}, #{status}, #{interactionMode},
                #{pid}, #{startedAt}, #{endedAt}, #{lastMessageAt}, #{exitCode}, #{exitReason}, #{rawLogPath},
                #{summary}, #{tagsJson}, #{extraJson}, #{createdAt}, #{updatedAt}
            )
            """)
    void insert(AiSession session);

    @Update("""
            UPDATE session
            SET status = #{status},
                pid = #{pid},
                started_at = #{startedAt},
                raw_log_path = #{rawLogPath},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateRuntime(@Param("id") String id,
                      @Param("status") String status,
                      @Param("pid") Long pid,
                      @Param("startedAt") String startedAt,
                      @Param("rawLogPath") String rawLogPath,
                      @Param("updatedAt") String updatedAt);

    @Update("""
            UPDATE session
            SET status = #{status},
                ended_at = #{endedAt},
                exit_code = #{exitCode},
                exit_reason = #{exitReason},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("endedAt") String endedAt,
                     @Param("exitCode") Integer exitCode,
                     @Param("exitReason") String exitReason,
                     @Param("updatedAt") String updatedAt);

    @Update("UPDATE session SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatusOnly(@Param("id") String id, @Param("status") String status, @Param("updatedAt") String updatedAt);

    @Update("UPDATE session SET last_message_at = #{lastMessageAt}, updated_at = #{updatedAt} WHERE id = #{id}")
    int touchLastMessage(@Param("id") String id,
                         @Param("lastMessageAt") String lastMessageAt,
                         @Param("updatedAt") String updatedAt);

    @Update("""
            UPDATE session
            SET summary = #{summary},
                tags_json = #{tagsJson},
                extra_json = #{extraJson},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateWorkspaceMetadata(@Param("id") String id,
                                @Param("summary") String summary,
                                @Param("tagsJson") String tagsJson,
                                @Param("extraJson") String extraJson,
                                @Param("updatedAt") String updatedAt);

    @Select("SELECT COUNT(1) FROM session")
    long countAll();

    @Select("SELECT COUNT(1) FROM session WHERE status IN ('STARTING', 'RUNNING', 'STOPPING')")
    long countRunning();
}
