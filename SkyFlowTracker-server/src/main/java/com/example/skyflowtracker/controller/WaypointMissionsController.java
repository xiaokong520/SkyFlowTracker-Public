package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.*;
import com.example.skyflowtracker.service.inte.WaypointMissionsServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/missions")
public class WaypointMissionsController {

    private final WaypointMissionsServiceInte waypointMissionsService;

    @Autowired
    public WaypointMissionsController(WaypointMissionsServiceInte waypointMissionsService) {
        this.waypointMissionsService = waypointMissionsService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> create(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody CreateMissionDto createMissionDto) throws Exception {
        Map<String, Object> result = waypointMissionsService.createMission(token, createMissionDto);
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse> update(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody UpdateMissionDto updateMissionDto) throws Exception {
        Map<String, Object> result = waypointMissionsService.updateMission(token, updateMissionDto);
        String message = (String) result.get("message");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> delete(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody DeleteMissionsDto deleteMissionsDto) throws Exception {
        String message = waypointMissionsService.deleteMissions(token, deleteMissionsDto);
        if (message.equals("删除成功")) {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getMissionsList")
    public ResponseEntity<ApiResponse> getMissionsList(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                                       @RequestParam(required = false) String keyword) throws Exception {
        Map<String, Object> result = waypointMissionsService.getMissionsList(token, page, pageSize, keyword);
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }

    @GetMapping("/getMissionDetail")
    public ResponseEntity<ApiResponse> getMissionDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                        @RequestParam("missionId") Long missionId) throws Exception {
        Map<String, Object> result = waypointMissionsService.getMissionDetail(token, missionId);
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }

    @PutMapping("/assign")
    public ResponseEntity<ApiResponse> assign(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody AssignMissionDto assignMissionDto) throws Exception {
        String message = waypointMissionsService.assignMission(token, assignMissionDto);
        if (message.equals("分派成功")) {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(message));
        }
    }

    @PutMapping("/updateStatus")
    public ResponseEntity<ApiResponse> updateStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                     @Valid @RequestBody UpdateMissionStatusDto updateMissionStatusDto) throws Exception {
        String message = waypointMissionsService.updateMissionStatus(token, updateMissionStatusDto);
        if (message.equals("状态更新成功")) {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(message));
        }
    }
}
