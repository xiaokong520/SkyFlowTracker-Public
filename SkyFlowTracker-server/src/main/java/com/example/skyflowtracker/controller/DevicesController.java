package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.DeleteDevicesDto;
import com.example.skyflowtracker.dto.UpdateDevicesDto;
import com.example.skyflowtracker.service.inte.DevicesServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/devices",produces = "application/json;charset=UTF-8")
public class DevicesController {
    DevicesServiceInte devicesServiceInte;

    @Autowired
    public DevicesController(DevicesServiceInte devicesServiceInte) {
        this.devicesServiceInte = devicesServiceInte;
    }

    @PostMapping("/addDevices")
    public ResponseEntity<ApiResponse> addDevices(@RequestParam(value = "file",required = false) MultipartFile file,
                                                  @RequestParam(value = "targetUserId",required = false) String targetUserId,
                                                  @RequestParam(value = "snCode",required = false) String snCode,
                                                  @RequestParam(value = "deviceData",required = false) String deviceData,
                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {

        Map<String,Object> message = devicesServiceInte.addDevices(file,snCode,targetUserId,deviceData,token);
        String messageStr = message.get("message").toString();
        Object data = message.get("data");
        if (messageStr.equals("添加成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(messageStr,data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(messageStr,data));
        }
    }

    @GetMapping("/getDevicesList")
    public ResponseEntity<ApiResponse> getDevicesList(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer size,
                                                      @RequestParam(value = "snCode",required = false) String snCode,
                                                      @RequestParam(value = "userId",required = false) String userId
                                                      ) throws Exception {
        Map<String,Object> map = devicesServiceInte.getDevicesList(token,page,size,snCode,userId);
        String message = map.get("message").toString();
        if (message.equals("获取成功")){
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,map.get("data")));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }

    }

    @PutMapping("/updateDevice")
    public ResponseEntity<ApiResponse> updateDevices(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                    @Valid @RequestBody UpdateDevicesDto updateDevicesDto) throws Exception {
        String message = devicesServiceInte.updateDevices(token,updateDevicesDto);
        if (message.equals("修改成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/checkDeviceBind")
    public ResponseEntity<ApiResponse> checkDeviceBind(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                       @RequestParam("sn") String sn) throws Exception {
        if (sn == null || sn.isEmpty()){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error("sn不能为空"));
        }
        boolean bound = devicesServiceInte.checkDeviceBind(token, sn);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("查询成功", bound));
    }

    @DeleteMapping("/deleteDevice")
    public ResponseEntity<ApiResponse> deleteDevices(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                     @Valid @RequestBody DeleteDevicesDto deleteDevicesDto) throws Exception {
        String message = devicesServiceInte.deleteDevices(token,deleteDevicesDto);
        if (message.equals("删除成功")){
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }

    }
}
