package com.example.skyflowtracker.exception;
/**
 * 定义和业务规则相关的枚举类
 */
public enum AppExceptionCodeMsg {

    TYPE_ERROR(400,"类型错误"),

    AUTH_ERROR(401,"账号不存在或账号密码错误"),
    USER_BLOCK(401,"账号已封禁"),
    USER_NOT_APPROVED(401,"该账号待审核"),

    INVALID_CODE(403,"验证码错误"),
    PERMISSION_DENIED(403,"您没有管理员权限"),

    REDIS_CONNECTION_FAILED(503, "Redis服务不可用"),

    CANNOT_DELETE_SELF(400, "不能删除自己"),
    USER_NOT_EXIST(404, "用户不存在"),

    NOT_FOLLOWING(400, "尚未关注该用户"),
    USER_EXIST(400, "用户已存在"),
    EMAIL_EXIST(400,"邮箱已存在"),
    INVALID_EMAIL(403,"邮箱格式错误"),
    CODE_EXIST(400,  "验证码已存在"),

    REQUEST_BODY_NOT_FOUND(400,"用户名或邮箱不能为空"),
    IMAGE_NOT_EXIST(400,"图片不能为空"),
    FILE_SAVE_ERROR(500,"保存文件失败"),

    QR_CODE_ERROR(400,"二维码解析失败，请确保上传的二维码图片清晰且包含有效信息"),
    DEVICE_DATA_ERROR(400,"解析设备数据错误，请确认传入的参数正确"),
    DEVICE_LIST_EMPTY(400, "设备列表不能为空"),
    DEVICE_NOT_EXIST(404, "设备不存在"),

    FLIGHTS_NOT_EXIST(404,"该条飞行记录不存在"),

    FLIGHTS_IS_END(400, "该飞行记录已结束"),
    FLIGHT_ID_IS_NULL(400,"飞行记录ID不能为空"),

    TASK_NOT_EXIST(404,"推理任务不存在"),
    INFERENCE_FAILED(500, "推理失败"),

    SHARE_NOT_EXIST(404, "分享链接不存在"),
    SHARE_EXPIRED(410, "分享链接已过期"),
    TASK_NOT_COMPLETED(400, "推理任务尚未完成，无法分享"),

    MISSION_NOT_EXIST(404, "航线任务不存在"),
    MISSION_NOT_DRAFT(400, "该航线任务不是草稿状态，无法编辑"),
    MISSION_ALREADY_ASSIGNED(400, "该航线任务已分派"),
    ASSIGNEE_NOT_EXIST(404, "被分派用户不存在"),
    FLY_ZONE_NOT_EXIST(404, "禁飞区不存在")

    ;

    private int code;
    private String message;

    AppExceptionCodeMsg(int code, String message) {
        this.code = code;
        this.message = message;
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
}
