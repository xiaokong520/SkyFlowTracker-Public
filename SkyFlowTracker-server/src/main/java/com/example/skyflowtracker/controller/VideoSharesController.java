package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.CreateShareDto;
import com.example.skyflowtracker.service.inte.VideoSharesServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/share")
public class VideoSharesController {

    private final VideoSharesServiceInte videoSharesService;

    @Autowired
    public VideoSharesController(VideoSharesServiceInte videoSharesService) {
        this.videoSharesService = videoSharesService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createShare(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @Valid @RequestBody CreateShareDto createShareDto) throws Exception {
        Map<String, Object> result = videoSharesService.createShare(token, createShareDto);
        String message = result.get("message").toString();
        if (message.equals("创建成功")) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(message, result.get("data")));
        } else {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/info/{shareCode}")
    public ResponseEntity<ApiResponse> getShareInfo(@PathVariable String shareCode) {
        Map<String, Object> result = videoSharesService.getShareInfo(shareCode);
        String message = result.get("message").toString();
        if (message.equals("获取成功")) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(message, result.get("data")));
        } else {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/myShares")
    public ResponseEntity<ApiResponse> getMyShares(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) throws Exception {
        Map<String, Object> result = videoSharesService.getMyShares(token, page, pageSize);
        String message = result.get("message").toString();
        if (message.equals("获取成功")) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(message, result.get("data")));
        } else {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> deleteShare(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam Long shareId) throws Exception {
        String message = videoSharesService.deleteShare(token, shareId);
        if (message.equals("删除成功")) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        } else {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }
}
