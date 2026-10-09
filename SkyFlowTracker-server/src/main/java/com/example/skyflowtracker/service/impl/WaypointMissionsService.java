package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.dto.*;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.mapper.WaypointMissionsMapper;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.pojo.WaypointMissions;
import com.example.skyflowtracker.service.inte.WaypointMissionsServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class WaypointMissionsService implements WaypointMissionsServiceInte {

    private final TokenUtil tokenUtil;
    private final WaypointMissionsMapper waypointMissionsMapper;
    private final UsersMapper usersMapper;

    @Autowired
    public WaypointMissionsService(TokenUtil tokenUtil, WaypointMissionsMapper waypointMissionsMapper, UsersMapper usersMapper) {
        this.tokenUtil = tokenUtil;
        this.waypointMissionsMapper = waypointMissionsMapper;
        this.usersMapper = usersMapper;
    }

    @Override
    public Map<String, Object> createMission(String token, CreateMissionDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();
        WaypointMissions mission = new WaypointMissions();
        mission.setName(dto.getName());
        mission.setDescription(dto.getDescription());
        mission.setWaypoints(dto.getWaypoints());
        mission.setCreatorId(userId);
        mission.setStatus(0); // 草稿
        mission.setTotalDistance(dto.getTotalDistance());
        mission.setEstimatedTime(dto.getEstimatedTime());
        mission.setCreateTime(now);
        mission.setModificationTime(now);

        waypointMissionsMapper.insertMission(mission);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "创建成功");
        Map<String, Object> data = new HashMap<>();
        data.put("id", mission.getId());
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> updateMission(String token, UpdateMissionDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        WaypointMissions existing = waypointMissionsMapper.selectMissionById(dto.getId());
        if (existing == null) {
            throw new AppException(AppExceptionCodeMsg.MISSION_NOT_EXIST);
        }
        if (existing.getStatus() != 0) {
            throw new AppException(AppExceptionCodeMsg.MISSION_NOT_DRAFT);
        }

        WaypointMissions mission = new WaypointMissions();
        mission.setId(dto.getId());
        mission.setName(dto.getName());
        mission.setDescription(dto.getDescription());
        mission.setWaypoints(dto.getWaypoints());
        mission.setTotalDistance(dto.getTotalDistance());
        mission.setEstimatedTime(dto.getEstimatedTime());
        mission.setModificationTime(LocalDateTime.now());

        waypointMissionsMapper.updateMission(mission);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "更新成功");
        return result;
    }

    @Override
    public String deleteMissions(String token, DeleteMissionsDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        int result = waypointMissionsMapper.deleteMissions(dto.getIds());
        if (result > 0) {
            return "删除成功";
        } else {
            return "删除失败";
        }
    }

    @Override
    public Map<String, Object> getMissionsList(String token, Integer page, Integer pageSize, String keyword) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer role = Integer.parseInt(checkToken.get("role").toString());

        String creatorId = null;
        String assigneeId = null;
        // 管理员查全部，普通用户仅查分派给自己的
        if (role != 1) {
            assigneeId = currentUserId;
        }

        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> missionsList = waypointMissionsMapper.selectMissionsList(creatorId, assigneeId, keyword, offset, pageSize);
        int total = waypointMissionsMapper.countMissions(creatorId, assigneeId, keyword);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("size", pageSize);
        pagination.put("total", total);
        pagination.put("totalPage", (int) Math.ceil((double) total / pageSize));

        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("missionsList", missionsList);
        data.put("pagination", pagination);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getMissionDetail(String token, Long missionId) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Integer role = Integer.parseInt(checkToken.get("role").toString());

        WaypointMissions mission;
        if (role == 1) {
            mission = waypointMissionsMapper.selectMissionById(missionId);
        } else {
            mission = waypointMissionsMapper.selectMissionByIdAndAssigneeId(missionId, currentUserId);
        }

        if (mission == null) {
            throw new AppException(AppExceptionCodeMsg.MISSION_NOT_EXIST);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("id", mission.getId());
        data.put("name", mission.getName());
        data.put("description", mission.getDescription());
        // 解析 waypoints JSON
        Object waypointsJson;
        String waypointsStr = mission.getWaypoints();
        if (waypointsStr != null && !waypointsStr.isEmpty()) {
            try {
                waypointsJson = objectMapper.readValue(waypointsStr, List.class);
            } catch (Exception e) {
                waypointsJson = new ArrayList<>();
            }
        } else {
            waypointsJson = new ArrayList<>();
        }
        data.put("waypoints", waypointsJson);
        data.put("creatorId", mission.getCreatorId());
        data.put("assigneeId", mission.getAssigneeId());
        data.put("status", mission.getStatus());
        data.put("totalDistance", mission.getTotalDistance());
        data.put("estimatedTime", mission.getEstimatedTime());
        data.put("createTime", mission.getCreateTime());
        data.put("modificationTime", mission.getModificationTime());
        result.put("data", data);
        return result;
    }

    @Override
    public String assignMission(String token, AssignMissionDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        // 检查任务是否存在
        WaypointMissions mission = waypointMissionsMapper.selectMissionById(dto.getMissionId());
        if (mission == null) {
            throw new AppException(AppExceptionCodeMsg.MISSION_NOT_EXIST);
        }

        // 检查被分派用户是否存在
        Users assignee = usersMapper.selectUserByUserId(dto.getAssigneeId());
        if (assignee == null) {
            throw new AppException(AppExceptionCodeMsg.ASSIGNEE_NOT_EXIST);
        }

        int result = waypointMissionsMapper.assignMission(dto.getMissionId(), dto.getAssigneeId(), 2, LocalDateTime.now());
        if (result > 0) {
            return "分派成功";
        } else {
            return "分派失败";
        }
    }

    @Override
    public String updateMissionStatus(String token, UpdateMissionStatusDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();

        WaypointMissions mission = waypointMissionsMapper.selectMissionByIdAndAssigneeId(dto.getMissionId(), currentUserId);
        if (mission == null) {
            throw new AppException(AppExceptionCodeMsg.MISSION_NOT_EXIST);
        }

        int currentStatus = mission.getStatus();
        int newStatus = dto.getStatus();

        // 校验状态转换合法性：2→3, 3→4, 2或3→5
        boolean valid = (currentStatus == 2 && newStatus == 3)
                || (currentStatus == 3 && newStatus == 4)
                || ((currentStatus == 2 || currentStatus == 3) && newStatus == 5);
        if (!valid) {
            return "状态转换不合法";
        }

        int result = waypointMissionsMapper.updateMissionStatus(dto.getMissionId(), currentUserId, newStatus, LocalDateTime.now());
        if (result > 0) {
            return "状态更新成功";
        } else {
            return "状态更新失败";
        }
    }
}
