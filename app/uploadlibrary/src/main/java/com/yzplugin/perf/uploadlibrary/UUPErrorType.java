package com.yzplugin.perf.uploadlibrary;

public enum UUPErrorType {
    NONE(0)                       ,//= 0,//无错误
    BAD_ACCESS(1000)              ,//= 1000，-1001,//需要重新登录
    BAD_PARAMS(1001)              ,//= 1001,//参数错误
    BAD_FUID(1002)                ,//= 1002,//fuid不存在
    BAD_SLICED(1003)              ,//= 1003, 103,//分片上传失败
    BAD_MIMETYPE(1004)            ,//= 1004,不支持的文件类型
    BAD_OTHER(1005)               ,//= 1005,未知服务器错误
    OVER_RETRY(1101)              ,//= 1101,//超过重试次数
    OVER_MAXSIZE(1102)            ,//= 1102,//超过大小
    OVER_MAXDURATION(1103)        ,//= 1103,//超过时长
    SLICED_FAIL(1104)             ,//= 1104,//分片失败
    LOW_NET(1105)                 ,//= 1105,//网络缓慢,连续10秒网速低于10KB/s
    BAD_NET(1106)                 ,//= 1106,//网络不通
    BAD_FILE(1107)                ,//= 1107,//文件不存在
    BAD_IO(1108)                  ;//= 1108,//文件读写错误，需要检查系统授权

    private int errCode;

    UUPErrorType(int code){
        this.errCode = code;
    }

    public int getValue(){
        return this.errCode;
    }
}
