package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.WaypointMissions;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface WaypointMissionsMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO waypoint_missions (name, description, waypoints, creator_id, assignee_id, status, total_distance, estimated_time, create_time, modification_time) " +
            "VALUES (#{name}, #{description}, #{waypoints}, #{creatorId}, #{assigneeId}, #{status}, #{totalDistance}, #{estimatedTime}, #{createTime}, #{modificationTime})")
    int insertMission(WaypointMissions mission);

    @Select("SELECT * FROM waypoint_missions WHERE id = #{id}")
    WaypointMissions selectMissionById(Long id);

    @Select("SELECT * FROM waypoint_missions WHERE id = #{id} AND assignee_id = #{assigneeId}")
    WaypointMissions selectMissionByIdAndAssigneeId(@Param("id") Long id, @Param("assigneeId") String assigneeId);

    @Update("UPDATE waypoint_missions SET assignee_id = #{assigneeId}, status = #{status}, modification_time = #{modificationTime} WHERE id = #{id}")
    int assignMission(@Param("id") Long id, @Param("assigneeId") String assigneeId, @Param("status") Integer status, @Param("modificationTime") LocalDateTime modificationTime);

    @Update("UPDATE waypoint_missions SET status = #{status}, modification_time = #{modificationTime} WHERE id = #{id} AND assignee_id = #{assigneeId}")
    int updateMissionStatus(@Param("id") Long id, @Param("assigneeId") String assigneeId, @Param("status") Integer status, @Param("modificationTime") LocalDateTime modificationTime);

    // 以下方法在 XML 中实现
    List<Map<String, Object>> selectMissionsList(@Param("creatorId") String creatorId, @Param("assigneeId") String assigneeId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);
    int countMissions(@Param("creatorId") String creatorId, @Param("assigneeId") String assigneeId, @Param("keyword") String keyword);
    int updateMission(WaypointMissions mission);
    int deleteMissions(@Param("ids") List<Long> ids);
}
