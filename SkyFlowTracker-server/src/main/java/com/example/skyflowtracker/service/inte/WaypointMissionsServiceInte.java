package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.*;

import java.util.Map;

public interface WaypointMissionsServiceInte {
    Map<String, Object> createMission(String token, CreateMissionDto dto) throws Exception;
    Map<String, Object> updateMission(String token, UpdateMissionDto dto) throws Exception;
    String deleteMissions(String token, DeleteMissionsDto dto) throws Exception;
    Map<String, Object> getMissionsList(String token, Integer page, Integer pageSize, String keyword) throws Exception;
    Map<String, Object> getMissionDetail(String token, Long missionId) throws Exception;
    String assignMission(String token, AssignMissionDto dto) throws Exception;
    String updateMissionStatus(String token, UpdateMissionStatusDto dto) throws Exception;
}
