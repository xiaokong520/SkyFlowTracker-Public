package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.DeleteInferenceTasksDto;
import com.example.skyflowtracker.dto.InferenceStartDto;
import com.example.skyflowtracker.dto.InferenceStopDto;
import com.example.skyflowtracker.service.inte.InferenceTasksServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inference")
public class InferenceTasksController {
    private InferenceTasksServiceInte inferenceTasksService;

    @Autowired
    public InferenceTasksController(InferenceTasksServiceInte inferenceTasksService) {
        this.inferenceTasksService = inferenceTasksService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse> start(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                             @Valid @RequestBody InferenceStartDto inferenceStartDto) throws Exception {
        Map<String,Object> result = inferenceTasksService.start(token,inferenceStartDto);
        String message = result.get("message").toString();
        if (message.equals("开始推理")){
            Object data = result.get("data");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message, data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/stop")
    public ResponseEntity<ApiResponse> stop(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                            @Valid @RequestBody InferenceStopDto inferenceStopDto) throws Exception {
        Map<String,Object> result = inferenceTasksService.stop(token,inferenceStopDto);
        String message = result.get("message").toString();
        if (message.equals("停止推理成功")){
            Object data = result.get("data");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message, data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/forceStop")
    public ResponseEntity<ApiResponse> forceStop(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                  @RequestParam("taskId") Long taskId) throws Exception {
        Map<String,Object> result = inferenceTasksService.forceStop(token, taskId);
        String message = result.get("message").toString();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(message));
    }

    @GetMapping("/getInferenceTasksList")
    public ResponseEntity<ApiResponse> getInferenceTasksList(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                             @RequestParam(required = false) Long flightId,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "10") Integer pageSize) throws Exception {
        Map<String,Object> result = inferenceTasksService.getInferenceTasksList(token,flightId,page,pageSize);
        String message = result.get("message").toString();
        if (message.equals("获取成功")){
            Object data = result.get("data");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message, data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getInferenceTasksDetail")
    public ResponseEntity<ApiResponse> getInferenceTasksDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                               @RequestParam("taskId") Long taskId) throws Exception {
        Map<String,Object> result = inferenceTasksService.getInferenceTasksDetail(token,taskId);
        String message = result.get("message").toString();
        if (message.equals("获取成功")){
            Object data = result.get("data");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message, data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @DeleteMapping("/deleteInferenceTasks")
    public ResponseEntity<ApiResponse> deleteInferenceTasks(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                            @Valid @RequestBody DeleteInferenceTasksDto deleteInferenceTasksDto) throws Exception {
        String message = inferenceTasksService.deleteInferenceTasks(token,deleteInferenceTasksDto);
        if (message.equals("删除成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PostMapping("/startOffline")
    public ResponseEntity<ApiResponse> startOffline(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                     @RequestParam("video") MultipartFile video,
                                                     @RequestParam(defaultValue = "yolov12m") String modelName,
                                                     @RequestParam(required = false) Double confidence) throws Exception {
        Map<String,Object> result = inferenceTasksService.startOfflineInference(token, video, modelName, confidence);
        String message = result.get("message").toString();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(message, result.get("data")));
    }

    @PostMapping("/startDemo")
    public ResponseEntity<ApiResponse> startDemo(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                  @RequestBody Map<String, String> body) throws Exception {
        String sn = body.get("sn");
        Map<String, Object> result = inferenceTasksService.startDemo(token, sn);
        String message = result.get("message").toString();
        Object data = result.get("data");
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    @PutMapping("/stopDemo")
    public ResponseEntity<ApiResponse> stopDemo(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                 @RequestBody Map<String, String> body) throws Exception {
        String sn = body.get("sn");
        Map<String, Object> result = inferenceTasksService.stopDemo(token, sn);
        String message = result.get("message").toString();
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/startLatencyTest")
    public ResponseEntity<ApiResponse> startLatencyTest(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                        @RequestBody Map<String, String> body) throws Exception {
        String sn = body.get("sn");
        Map<String, Object> result = inferenceTasksService.startLatencyTest(token, sn);
        String message = result.get("message").toString();
        Object data = result.get("data");
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    @PutMapping("/stopLatencyTest")
    public ResponseEntity<ApiResponse> stopLatencyTest(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                       @RequestBody Map<String, String> body) throws Exception {
        String sn = body.get("sn");
        Map<String, Object> result = inferenceTasksService.stopLatencyTest(token, sn);
        String message = result.get("message").toString();
        return ResponseEntity.ok(ApiResponse.success(message));
    }
}
