package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.DeleteInferenceTasksDto;
import com.example.skyflowtracker.dto.InferenceStartDto;
import com.example.skyflowtracker.dto.InferenceStopDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface InferenceTasksServiceInte {
    Map<String, Object> start(String token, InferenceStartDto inferenceStartDto) throws Exception;

    Map<String, Object> stop(String token, InferenceStopDto inferenceStopDto) throws Exception;

    Map<String, Object> forceStop(String token, Long taskId) throws Exception;

    Map<String, Object> getInferenceTasksList(String token, Long flightId, Integer page, Integer pageSize) throws Exception;

    Map<String, Object> getInferenceTasksDetail(String token, Long taskId) throws Exception;

    String deleteInferenceTasks(String token, @Valid DeleteInferenceTasksDto deleteInferenceTasksDto) throws Exception;

    boolean hasRunningTask(String sn);

    Map<String, Object> startOfflineInference(String token, MultipartFile video, String modelName, Double confidence) throws Exception;

    Map<String, Object> startDemo(String token, String sn) throws Exception;

    Map<String, Object> stopDemo(String token, String sn) throws Exception;

    Map<String, Object> startLatencyTest(String token, String sn) throws Exception;

    Map<String, Object> stopLatencyTest(String token, String sn) throws Exception;

    void autoStopBySn(String sn);
}
