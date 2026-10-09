package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.Flights;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FlightsMapper {

    @Select("select * from flights where id = #{id}")
    Flights selectFlightsById(Long id);
    @Select("select * from flights where id = #{id} and user_id = #{userId}")
    Flights selectFlightsByIdAndUserId(Long id,String userId);
    @Select("select * from flights where sn = #{sn} and status = 0 order by start_time desc limit 1")
    Flights selectRunningFlightBySn(String sn);

    List<Map<String,Object>> selectFlightsList(String userId, String keyword, int offset, int size);

    //更新航线json
    @Update("update flights set flight_path = #{flightPath},modification_time = #{modificationTime} where id = #{id} and user_id = #{userId}")
    int updateFlightPath(Long id, String userId, String flightPath, LocalDateTime  modificationTime);

    //追加航线坐标点（直接在SQL层拼接，避免读取整个BLOB）
    @Update("update flights set flight_path = CASE WHEN flight_path IS NULL OR flight_path = '' THEN #{newPoints} ELSE JSON_MERGE_PRESERVE(flight_path, #{newPoints}) END, modification_time = #{modificationTime} where id = #{id} and user_id = #{userId}")
    int appendFlightPath(Long id, String userId, String newPoints, LocalDateTime modificationTime);

    @Options(useGeneratedKeys = true,keyProperty = "id")
    @Insert("insert into flights (user_id, sn, home_latitude, home_longitude, start_time, status, create_time, modification_time)" +
            " values (#{userId}, #{sn}, #{homeLatitude}, #{homeLongitude}, #{startTime}, #{status}, #{createTime}, #{modificationTime})")
    int insertFlight(Flights flights);

    int countFlights(String userId, String keyword);
    int endFlights(Flights flights);

    int deleteFlights(List<Long> ids);

    @Update("update flights set status = 1, end_time = #{endTime}, modification_time = #{modificationTime} where id = #{id}")
    int completeFlightById(@Param("id") Long id, @Param("endTime") LocalDateTime endTime, @Param("modificationTime") LocalDateTime modificationTime);
}
