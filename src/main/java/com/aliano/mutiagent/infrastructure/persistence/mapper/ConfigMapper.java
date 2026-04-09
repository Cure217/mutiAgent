package com.aliano.mutiagent.infrastructure.persistence.mapper;

import com.aliano.mutiagent.domain.config.SystemConfig;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ConfigMapper {

    @Select("""
            SELECT * FROM config
            ORDER BY config_group ASC, config_key ASC
            """)
    List<SystemConfig> findAll();

    @Select("""
            SELECT * FROM config
            WHERE config_group = #{configGroup}
              AND config_key = #{configKey}
            """)
    SystemConfig findByGroupAndKey(@Param("configGroup") String configGroup, @Param("configKey") String configKey);

    @Insert("""
            INSERT INTO config (
                id, config_group, config_key, value_type, value_text, value_json, secret_ref, remark, updated_at
            ) VALUES (
                #{id}, #{configGroup}, #{configKey}, #{valueType}, #{valueText}, #{valueJson}, #{secretRef}, #{remark}, #{updatedAt}
            )
            ON CONFLICT(config_group, config_key) DO UPDATE SET
                value_type = excluded.value_type,
                value_text = excluded.value_text,
                value_json = excluded.value_json,
                secret_ref = excluded.secret_ref,
                remark = excluded.remark,
                updated_at = excluded.updated_at
            """)
    void upsert(SystemConfig config);
}
