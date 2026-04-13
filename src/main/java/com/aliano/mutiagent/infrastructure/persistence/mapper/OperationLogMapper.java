package com.aliano.mutiagent.infrastructure.persistence.mapper;

import com.aliano.mutiagent.domain.log.OperationLogRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OperationLogMapper {

    @Select({
            "<script>",
            "SELECT * FROM operation_log",
            "WHERE 1 = 1",
            "<if test='targetType != null and targetType != \"\"'> AND target_type = #{targetType}</if>",
            "<if test='targetId != null and targetId != \"\"'> AND target_id = #{targetId}</if>",
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>",
            "<if test='operatorName != null and operatorName != \"\"'> AND operator_name = #{operatorName}</if>",
            "ORDER BY created_at DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<OperationLogRecord> findPage(@Param("targetType") String targetType,
                                      @Param("targetId") String targetId,
                                      @Param("action") String action,
                                      @Param("operatorName") String operatorName,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(1) FROM operation_log",
            "WHERE 1 = 1",
            "<if test='targetType != null and targetType != \"\"'> AND target_type = #{targetType}</if>",
            "<if test='targetId != null and targetId != \"\"'> AND target_id = #{targetId}</if>",
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>",
            "<if test='operatorName != null and operatorName != \"\"'> AND operator_name = #{operatorName}</if>",
            "</script>"
    })
    long countPage(@Param("targetType") String targetType,
                   @Param("targetId") String targetId,
                   @Param("action") String action,
                   @Param("operatorName") String operatorName);

    @Insert("""
            INSERT INTO operation_log (
                id, target_type, target_id, action, result, operator_name, detail_json, created_at
            ) VALUES (
                #{id}, #{targetType}, #{targetId}, #{action}, #{result}, #{operatorName}, #{detailJson}, #{createdAt}
            )
            """)
    void insert(OperationLogRecord record);
}
