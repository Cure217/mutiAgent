package com.aliano.mutiagent.infrastructure.persistence.mapper;

import com.aliano.mutiagent.domain.instance.AppInstance;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AppInstanceMapper {

    @Select({
            "<script>",
            "SELECT * FROM app_instance",
            "WHERE 1 = 1",
            "<if test='appType != null and appType != \"\"'> AND app_type = #{appType}</if>",
            "<if test='enabled != null'> AND enabled = #{enabled}</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (name LIKE '%' || #{keyword} || '%' OR code LIKE '%' || #{keyword} || '%')",
            "</if>",
            "ORDER BY updated_at DESC, created_at DESC",
            "</script>"
    })
    List<AppInstance> findAll(@Param("appType") String appType,
                              @Param("enabled") Integer enabled,
                              @Param("keyword") String keyword);

    @Select("SELECT * FROM app_instance WHERE id = #{id}")
    AppInstance findById(@Param("id") String id);

    @Insert("""
            INSERT INTO app_instance (
                id, code, name, app_type, adapter_type, runtime_env, launch_mode,
                executable_path, launch_command, args_json, workdir, env_json,
                path_mapping_rule, enabled, auto_restart, remark, last_start_at,
                created_at, updated_at
            ) VALUES (
                #{id}, #{code}, #{name}, #{appType}, #{adapterType}, #{runtimeEnv}, #{launchMode},
                #{executablePath}, #{launchCommand}, #{argsJson}, #{workdir}, #{envJson},
                #{pathMappingRule}, #{enabled}, #{autoRestart}, #{remark}, #{lastStartAt},
                #{createdAt}, #{updatedAt}
            )
            """)
    void insert(AppInstance instance);

    @Update("""
            UPDATE app_instance
            SET name = #{name},
                app_type = #{appType},
                adapter_type = #{adapterType},
                runtime_env = #{runtimeEnv},
                launch_mode = #{launchMode},
                executable_path = #{executablePath},
                launch_command = #{launchCommand},
                args_json = #{argsJson},
                workdir = #{workdir},
                env_json = #{envJson},
                path_mapping_rule = #{pathMappingRule},
                enabled = #{enabled},
                auto_restart = #{autoRestart},
                remark = #{remark},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(AppInstance instance);

    @Update("UPDATE app_instance SET enabled = #{enabled}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateEnabled(@Param("id") String id, @Param("enabled") Integer enabled, @Param("updatedAt") String updatedAt);

    @Update("UPDATE app_instance SET last_start_at = #{lastStartAt}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateLastStartAt(@Param("id") String id, @Param("lastStartAt") String lastStartAt, @Param("updatedAt") String updatedAt);

    @Update("UPDATE app_instance SET executable_path = #{executablePath}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateExecutablePath(@Param("id") String id,
                             @Param("executablePath") String executablePath,
                             @Param("updatedAt") String updatedAt);

    @Select("SELECT COUNT(1) FROM app_instance")
    long countAll();
}
