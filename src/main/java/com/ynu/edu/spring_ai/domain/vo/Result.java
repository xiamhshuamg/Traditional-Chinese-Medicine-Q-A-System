package com.ynu.edu.spring_ai.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class Result {
    private Integer ok;
    private String msg;
    private Map<String, Object> data;

    private Result(Integer ok, String msg) {
        this.ok = ok;
        this.msg = msg;
    }

    private Result(Integer ok, String msg, Map<String, Object> data) {
        this.ok = ok;
        this.msg = msg;
        this.data = data;
    }

    public static Result ok() {
        return new Result(1, "ok");
    }

    public static Result ok(Map<String, Object> data) {
        return new Result(1, "ok", data);
    }

    public static Result fail(String msg) {
        return new Result(0, msg);
    }

    // 添加setter方法用于设置数据
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}