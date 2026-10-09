package com.example.skyflowtracker.vo;

import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL) //忽略为null的字段
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public ApiResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    //两个参数
    public static <T> ApiResponse<T> success(T message) {
        return new ApiResponse<>(1, message.toString());
    }
    public static <T> ApiResponse<T> error(T message) {
        return new ApiResponse<>(0, message.toString());
    }
    //三个参数
    public static <T> ApiResponse<T> success(T message, T data) {
        return new ApiResponse<>(1, message.toString(), data);
    }
    public static <T> ApiResponse<T> error(T message, T data) {
        return new ApiResponse<>(0, message.toString(), data);
    }
    //全局异常处理返回
    public static <T> ApiResponse<T> error(AppExceptionCodeMsg appExceptionCodeMsg){
        return new ApiResponse<>(appExceptionCodeMsg.getCode(),appExceptionCodeMsg.getMessage());
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
