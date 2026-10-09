package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.FlyZones;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface FlyZonesMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO fly_zones (dji_fly_zone_id, name, category, shape, center_lat, center_lng, radius, polygon_points, max_altitude, description, source, creator_id, enabled, create_time, modification_time) " +
            "VALUES (#{djiFlyZoneId}, #{name}, #{category}, #{shape}, #{centerLat}, #{centerLng}, #{radius}, #{polygonPoints}, #{maxAltitude}, #{description}, #{source}, #{creatorId}, #{enabled}, #{createTime}, #{modificationTime})")
    int insertFlyZone(FlyZones flyZone);

    @Select("SELECT * FROM fly_zones WHERE id = #{id}")
    FlyZones selectFlyZoneById(Long id);

    @Select("SELECT * FROM fly_zones WHERE dji_fly_zone_id = #{djiFlyZoneId}")
    FlyZones selectByDjiFlyZoneId(Integer djiFlyZoneId);

    @Select("SELECT * FROM fly_zones WHERE enabled = 1")
    List<FlyZones> selectAllEnabledFlyZones();

    List<Map<String, Object>> selectFlyZonesList(@Param("keyword") String keyword, @Param("category") Integer category, @Param("offset") int offset, @Param("size") int size);
    int countFlyZones(@Param("keyword") String keyword, @Param("category") Integer category);
    int updateFlyZone(FlyZones flyZone);
    int deleteFlyZones(@Param("ids") List<Long> ids);
}