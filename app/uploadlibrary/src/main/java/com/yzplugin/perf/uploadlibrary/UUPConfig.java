package com.yzplugin.perf.uploadlibrary;

public class UUPConfig {
    long size = 0; // 大小限制
    int count = 0; //最多最多选择限制
    int live = 0; //最多并行操作限制
    int duration = 0;//文件时长限制200秒
    int perChunks = 0;//单个分片大小
    int retryTimes = 0;//单个分片上传失败尝试次数
    String serverUri = null;
    String authSign = null;
    String card = null;

    public UUPConfig(){
        size = 2L * 1024 * 1024 * 1024;
        count = 9;
        live = 3;
        retryTimes = 3;
        duration = 200;
        perChunks = 5 * 1024 * 1024;
        serverUri = "http://192.168.201.100/vgc/newscctv/v1/fileupload_170202.php?";
        authSign = "Y2RiY1A3S0tpeXg5d1A3YnlPZUNWQWhIQWUyT3kzZHlFQi9zVXBhVi9Yd0tCcHkyVmdyZGJGNjNqdHRoWEZ2T3h3MUVqanI5d3Q1TjVyQzhScUJ4NFp4aDVZa2YvaDNvaGFrRnBsZ0crL0ZRUDZUZXVuMURtM0FpTHF4aXROVUo5ZmwzZzZvYVZDTFRFOTMwR1hHV2M4K0I1Z3VFZHN6T1N3aTc3YkN1S2s0R1c2SDYyR1pGMlZhb2dGL3dmNUU1V21zaDUyVlVGaFRYeVlSZHhtL09PZnNacVJWMGZQaVJmV2JjL0RJZE5iMWUzU3JFdm9OeG85RkxabWNYOTY4QzZOdnc=";
        card = "5336";
    }
}
