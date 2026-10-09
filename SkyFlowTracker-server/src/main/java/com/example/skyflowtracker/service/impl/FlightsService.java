package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.dto.DeleteFlightsDto;
import com.example.skyflowtracker.dto.FlightsEndDto;
import com.example.skyflowtracker.dto.FlightsStartDto;
import com.example.skyflowtracker.dto.FlightsUpdatePathDto;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.DevicesMapper;
import com.example.skyflowtracker.mapper.FlightsMapper;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.pojo.Devices;
import com.example.skyflowtracker.pojo.Flights;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.service.inte.FlightsServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.oer.its.etsi102941.SharedAtRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FlightsService implements FlightsServiceInte {

    private TokenUtil tokenUtil;
    private FlightsMapper flightsMapper;
    private UsersMapper usersMapper;
    private DevicesMapper devicesMapper;

    @Autowired
    public FlightsService(TokenUtil tokenUtil, FlightsMapper flightsMapper, UsersMapper usersMapper, DevicesMapper devicesMapper) {
        this.tokenUtil = tokenUtil;
        this.flightsMapper = flightsMapper;
        this.usersMapper = usersMapper;
        this.devicesMapper = devicesMapper;
    }

    @Override
    public Map<String, Object> start(String token, FlightsStartDto flightsStartDto) throws Exception {
        //获取当前用户判断账号状态
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users users = usersMapper.selectUserByUserId(userId);
        if (users == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        Integer userRole = users.getStatus();
        if (userRole == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (userRole == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //判断设备是否存在
        Devices devices = devicesMapper.selectDeviceBySnAndUserId(flightsStartDto.getSn(),userId);
        if (devices == null){
            throw new AppException(AppExceptionCodeMsg.DEVICE_NOT_EXIST);
        }
        //构建飞行记录
        LocalDateTime now = LocalDateTime.now();
        Flights flights = new Flights();
        flights.setUserId(userId);
        flights.setSn(flightsStartDto.getSn());
        flights.setHomeLatitude(flightsStartDto.getHome_latitude());
        flights.setHomeLongitude(flightsStartDto.getHome_longitude());
        flights.setStartTime(now);
        flights.setStatus(0);
        flights.setCreateTime(now);
        flights.setModificationTime(now);
        //插入数据
        int result = flightsMapper.insertFlight(flights);
        Map<String,Object> map = new HashMap<>();
        if (result > 0){
            map.put("message","启动成功");
            Map<String,Object> data = new HashMap<>();
            data.put("flightId",flights.getId());
            map.put("data",data);
        }else {
            map.put("message","启动失败");
        }
        return map;
    }

    @Override
    public String end(String token, FlightsEndDto flightsEndDto) throws Exception {
        //获取userId
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        //查询该条飞行记录是否存在
        Long id = flightsEndDto.getFlightId();
        Flights flights = flightsMapper.selectFlightsByIdAndUserId(id,userId);
        if (flights == null){
            throw new AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
        }
        //查询飞行记录是否已结束
        if (flights.getStatus() !=0){
            throw new AppException(AppExceptionCodeMsg.FLIGHTS_IS_END);
        }
        //计算飞行时长
        LocalDateTime now = LocalDateTime.now();
        long duration = Duration.between(flights.getStartTime(),now).getSeconds();
        //更新飞行记录
        flights.setEndTime(now);
        flights.setDuration((int) duration);
        flights.setMaxAltitude(flightsEndDto.getMaxAltitude() != null ? flightsEndDto.getMaxAltitude() : 0);
        flights.setMaxSpeed(flightsEndDto.getMaxSpeed() != null ? flightsEndDto.getMaxSpeed() : 0);
        flights.setDistance(flightsEndDto.getDistance() != null ? flightsEndDto.getDistance() : 0);
        flights.setStatus(flightsEndDto.getStatus() != null ? flightsEndDto.getStatus() : 1);
        flights.setModificationTime(now);
        //修改数据库
        int result = flightsMapper.endFlights(flights);
        if (result > 0){
            //更新设备累计航程和飞行时间
            long distanceLong = flightsEndDto.getDistance() != null ? flightsEndDto.getDistance().longValue() : 0;
            devicesMapper.addFlightStats(flights.getSn(), userId, distanceLong, duration, now);
            return "飞行记录已更新";
        }else {
            return "飞行记录更新失败";
        }
    }

    @Override
    public String updatePath(String token, FlightsUpdatePathDto flightsUpdatePathDto) throws Exception {
        //获取userId
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Long flightId = flightsUpdatePathDto.getFlightId();
        //查询飞行记录是否存在
        Flights flights = flightsMapper.selectFlightsByIdAndUserId(flightId,userId);
        if (flights == null){
            throw new  AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
        }
        //将新坐标点序列化为JSON数组，直接在SQL层追加
        ObjectMapper objectMapper = new ObjectMapper();
        String newPoints = objectMapper.writeValueAsString(flightsUpdatePathDto.getPoints());
        //更新数据库（SQL层拼接，无需读取整个BLOB）
        LocalDateTime now = LocalDateTime.now();
        int result = flightsMapper.appendFlightPath(flightId,userId,newPoints,now);
        if (result > 0){
            return "更新成功";
        }else {
            return "更新失败";
        }
    }

    @Override
    public Map<String, Object> getFlightsList(String token, Integer page, Integer pageSize, String keyword) throws Exception {
        //获取userId
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());
        String userId = null;
        //只有管理员才可获取所有用户的飞行记录
        //普通用户只能获取自己的飞行记录
        if (userRole != 1){
            userId = currentUserId;
        }
        //计算分页参数
        int offset = (page - 1) * pageSize;
        //查询列表和总数
        List<Map<String,Object>> flightsList = flightsMapper.selectFlightsList(userId,keyword,offset,pageSize);
        int total = flightsMapper.countFlights(userId,keyword);
        //构建分页信息
        Map<String,Object> pagination = new HashMap<>();
        pagination.put("page",page);
        pagination.put("size",pageSize);
        pagination.put("total",total);
        pagination.put("totalPage",(int) Math.ceil((double)total/pageSize));
        //构建返回数据
        Map<String,Object> result = new HashMap<>();
        result.put("message","获取成功");
        Map<String,Object> data = new HashMap<>();
        data.put("flightsList",flightsList);
        data.put("pagination",pagination);
        result.put("data",data);
        return result;
    }

    @Override
    public Map<String, Object> getFlightDetail(String token, Long flightId) throws Exception {
        //获取userId
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());
        //查询所有飞行记录
        Flights flights;
        if (userRole == 1){
            //管理员查所有用户记录
            flights = flightsMapper.selectFlightsById(flightId);
        }else {
            //普通用户只可查询自己的飞行记录
            flights = flightsMapper.selectFlightsByIdAndUserId(flightId,currentUserId);
        }
        if (flights == null){
            throw new  AppException(AppExceptionCodeMsg.FLIGHTS_NOT_EXIST);
        }
        //构建返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("id", flights.getId());
        data.put("userId", flights.getUserId());
        data.put("sn", flights.getSn());
        //解析 flightPath 为 JSON 数组
        ObjectMapper objectMapper = new ObjectMapper();
        Object flightPathJson;
        String pathStr = flights.getFlightPath();
        if (pathStr != null && !pathStr.isEmpty()) {
            try {
                flightPathJson = objectMapper.readValue(pathStr, List.class);
            } catch (Exception e) {
                flightPathJson = new ArrayList<>();
            }
        } else {
            flightPathJson = new ArrayList<>();
        }
        data.put("flightPath", flightPathJson);
        data.put("homeLatitude", flights.getHomeLatitude());
        data.put("homeLongitude", flights.getHomeLongitude());
        data.put("startTime", flights.getStartTime());
        data.put("endTime", flights.getEndTime());
        data.put("duration", flights.getDuration());
        data.put("maxAltitude", flights.getMaxAltitude());
        data.put("maxSpeed", flights.getMaxSpeed());
        data.put("distance", flights.getDistance());
        data.put("status", flights.getStatus());
        result.put("data", data);
        return result;
    }

    @Override
    public String deleteFlights(String token, DeleteFlightsDto deleteFlightsDto) throws Exception {
        //判断用户是否时管理员，只有管理员才可删除飞行记录
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());
        if (userRole != 1){
            throw new  AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }
        //删除数据
        int result = flightsMapper.deleteFlights(deleteFlightsDto.getIds());
        if (result > 0){
            return "删除成功";
        }else {
            return "删除失败";
        }
    }
}
