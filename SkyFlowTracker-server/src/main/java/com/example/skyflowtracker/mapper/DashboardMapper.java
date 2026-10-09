package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.pojo.InferenceTasks;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    int countTotalFlights(@Param("userId") String userId);

    Long sumTotalDetections(@Param("userId") String userId);

    Long sumFlightDuration(@Param("userId") String userId);

    int countTotalDevices(@Param("userId") String userId);

    List<Map<String, Object>> selectDetectionTrend(@Param("userId") String userId, @Param("range") String range);

    List<Map<String, Object>> selectFlightActivity(@Param("userId") String userId, @Param("range") String range);

    List<Map<String, Object>> selectFlightLocations(@Param("userId") String userId);

    List<Map<String, Object>> selectDeviceRanking(@Param("userId") String userId);

    List<InferenceTasks> selectCompletedTasksResultData(@Param("userId") String userId);

    @Select("select count(*) from users")
    int countTotalUsers();

    List<Map<String, Object>> selectUserActivityRanking();
}
