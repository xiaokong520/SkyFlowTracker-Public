package com.example.skyflowtracker.exception;

public class AppException extends RuntimeException{
    private int code;
    private String message;

    public AppException(AppExceptionCodeMsg appExceptionCodeMsg) {
        super();
        this.code = appExceptionCodeMsg.getCode();
        this.message = appExceptionCodeMsg.getMessage();
    }

    public AppException(int code, String message) {
        super();
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
