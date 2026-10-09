package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.DeleteFlightsDto;
import com.example.skyflowtracker.dto.FlightsEndDto;
import com.example.skyflowtracker.dto.FlightsStartDto;
import com.example.skyflowtracker.dto.FlightsUpdatePathDto;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.service.inte.FlightsServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightsController {
    private FlightsServiceInte flightsService;

    @Autowired
    public FlightsController(FlightsServiceInte flightsService) {
        this.flightsService = flightsService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse> start(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                             @Valid @RequestBody FlightsStartDto flightsStartDto) throws Exception {
        Map<String,Object> result = flightsService.start(token,flightsStartDto);
        String message = (String) result.get("message");
        Object data = result.get("data");
        if (message.equals("启动成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/end")
    public ResponseEntity<ApiResponse> end(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                            @Valid @RequestBody FlightsEndDto flightsEndDto) throws Exception {
        String message = flightsService.end(token,flightsEndDto);
        if (message.equals("飞行记录已更新")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/updatePath")
    public ResponseEntity<ApiResponse> updatePath(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                  @Valid @RequestBody FlightsUpdatePathDto  flightsUpdatePathDto) throws Exception {
        String message = flightsService.updatePath(token,flightsUpdatePathDto);
        if (message.equals("更新成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getFlightsList")
    public ResponseEntity<ApiResponse> getFlightsList(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                                      @RequestParam(required = false) String keyword) throws Exception {
        Map<String,Object> result = flightsService.getFlightsList(token,page,pageSize,keyword);
        String message = (String) result.get("message");
        Object data = result.get("data");
        if (message.equals("获取成功")){
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,data));
        }else {
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getFlightDetail")
    public ResponseEntity<ApiResponse> getFlightDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                       @RequestParam("flightId") Long flightId) throws Exception {
        if (flightId == null){
            throw new AppException(AppExceptionCodeMsg.FLIGHT_ID_IS_NULL);
        }
        Map<String,Object> result = flightsService.getFlightDetail(token,flightId);
        String message = (String) result.get("message");
        Object data = result.get("data");
        if (message.equals("获取成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @DeleteMapping("/deleteFlights")
    public ResponseEntity<ApiResponse> deleteFlights(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                     @Valid @RequestBody DeleteFlightsDto deleteFlightsDto) throws Exception {
        String message = flightsService.deleteFlights(token,deleteFlightsDto);
        if (message.equals("删除成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

}
