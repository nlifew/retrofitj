package com.example.myapplication;

/**
 * @author wangaihu
 * @date 2021/11/24
 * @desc
 */
public class Bean {

    public int errno;
    public String errMsg;
    public String data;

    @Override
    public String toString() {
        return "Bean{" +
                "errno=" + errno +
                ", errMsg='" + errMsg + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}
