package com.yzplugin.perf.uploadlibrary;

public class UUPConfig {
    public long size; // 大小限制
    public int count; //最多最多选择限制
    public int live; //最多并行操作限制
    public int duration;//文件限制时长单位秒
    public int perChunks;//单个分片大小
    public int retryTimes;//单个分片上传失败尝试次数
    public String fuidURi;
    public String serverUri;
    public String authSign;
    public String deviceToken;
    public String card;

    public UUPConfig(){
        size = 2L * 1024 * 1024 * 1024;
        count = 9;
        live = 3;
        retryTimes = 3;
        duration = 2 * 60 * 60;
        perChunks = 5 * 1024 * 1024;
        fuidURi = "";
        serverUri = "";
        authSign = "";
        deviceToken = "";
        card = "";
    }
}
