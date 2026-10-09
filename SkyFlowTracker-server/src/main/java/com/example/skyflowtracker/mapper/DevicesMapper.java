package com.example.skyflowtracker.mapper;

import com.example.skyflowtracker.dto.DeleteDevicesDto;
import com.example.skyflowtracker.dto.UpdateDevicesDto;
import com.example.skyflowtracker.pojo.Devices;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DevicesMapper {

    @Select("select * from devices where sn = #{sn}")
    Devices selectDeviceBySn(String sn);
    @Select("select * from devices where sn = #{sn} and user_id = #{userId}")
    Devices selectDeviceBySnAndUserId(String sn, String userId);

    // 累加设备的飞行时间和航程
    @Update("UPDATE devices SET accumulated_voyage = IFNULL(accumulated_voyage, 0) + #{distance}, " +
            "fly_time = IFNULL(fly_time, 0) + #{duration}, " +
            "modification_time = #{modificationTime} " +
            "WHERE sn = #{sn} AND user_id = #{userId}")
    int addFlightStats(@Param("sn") String sn,
                       @Param("userId") String userId,
                       @Param("distance") Long distance,
                       @Param("duration") Long duration,
                       @Param("modificationTime") LocalDateTime modificationTime);


    int insertDevices(List<Devices> devicesList);

    List<Map<String, Object>> selectDevicesList(String userId, String snCode, int offset, Integer size);

    int countDevices(String userId, String snCode);

    int updateDevices(UpdateDevicesDto updateDevicesDto);

    int deleteDevices(DeleteDevicesDto deleteDevicesDto);
}
