package com.example.skyflowtracker.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.example.skyflowtracker.vo.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
//全局异常处理
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler<T> {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        String errorMessage = ex.getBindingResult().getFieldError().getDefaultMessage();
        ApiResponse<T> apiResponse = new ApiResponse<T>(HttpStatus.BAD_REQUEST.value(),errorMessage);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public <T> ApiResponse<T> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return new ApiResponse<T>(400, "请求体缺失或格式错误");
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<T>> handleRuntimeException(RuntimeException ex) {
        log.error("运行时异常：{}",ex.getMessage());
        if (ex instanceof AppException){
            AppException appException = (AppException) ex;
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ApiResponse<>(appException.getCode(), appException.getMessage()));
        }
        return ResponseEntity
                .status(500)
                .body(new ApiResponse<>(500, "服务器内部错误，请联系管理员后重试"));
    }
}
