package com.yzplugin.perf.uploadlibrary;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

class UUPItemFUID implements Runnable {

    protected UUPItem mItem;
    UUPItemFUID(WeakReference<UUPItem> item){
        mItem = item.get();
    }


    private void getFUID(){
        try {
            String path = mItem.mConfig.fuidURi;
            if (mItem.mFUID != null){
                path = path + "?fuid="+mItem.mFUID+"&total="+mItem.mSliced.mTotalChunks;
            }

            // 请求的参数转换为byte数组
//            String params = "Auth-Sign="+ URLEncoder.encode(mItem.mConfig.authSign, "UTF-8")
//                    + "&Device-Token=" + URLEncoder.encode(mItem.mConfig.deviceToken, "UTF-8");
//
//            path = path + "&" + params;
            // 新建一个URL对象
            URL url = new URL(path);
            // 打开一个HttpURLConnection连接
            HttpURLConnection urlConn = null;
            urlConn = (HttpURLConnection) url.openConnection();
            // 设置连接超时时间
            urlConn.setConnectTimeout(20 * 1000);
            urlConn.setReadTimeout(20 * 1000);
            // Post请求必须设置允许输出
            urlConn.setDoOutput(true);
            // Post请求不能使用缓存
            urlConn.setUseCaches(false);
            // 设置为Post请求
            urlConn.setRequestMethod("GET");
            urlConn.setRequestProperty("Auth-Sign", mItem.mConfig.authSign);
            urlConn.setRequestProperty("Device-Token", mItem.mConfig.deviceToken);
            // 开始连接
            urlConn.connect();
            // 判断请求是否成功
            if (urlConn.getResponseCode() == 200) {
                // 获取返回的数据
                InputStream inputStream = urlConn.getInputStream();
                byte[] data=new byte[1024];
                StringBuilder buffer = new StringBuilder();
                int length=0;
                while ((length=inputStream.read(data))!=-1){
                    String s=new String(data, 0,length);
                    buffer.append(s);
                }
                inputStream.close();
                Map<String,String> result = excuteData(buffer.toString());
                if(result != null){
                    String code = result.get("errorCode");
                    String status = result.get("status");
                    if(code == null)code = "";
                    if("FAIL".equals(status)){
                        mItem.pause();
                        switch (code) {
                            case "-1":
                                mItem.mError = UUPErrorType.BAD_MIMETYPE;
                                break;
                            case "-1001":
                            case "1000":
                                mItem.mError = UUPErrorType.BAD_ACCESS;
                                break;
                            case "1001":
                                mItem.mError = UUPErrorType.BAD_PARAMS;
                                break;
                            case "1002":
                                mItem.mError = UUPErrorType.BAD_FUID;
                                break;
                            case "1003":
                                mItem.mError = UUPErrorType.BAD_SLICED;
                                mItem.next();//传下一片
                                break;
                            default:
                                mItem.mError = UUPErrorType.BAD_OTHER;
                                break;
                        }
                        mItem.mHander.sendEmptyMessage(0);
                        if(!mItem.isCancel)mItem.cancle();
                    }else {
                        String fuid = result.get("fuid");
                        if(fuid != null){
                            mItem.mError = UUPErrorType.NONE;
                            mItem.mFUID = fuid;
                            mItem.preStart();
                        }else {
                            mItem.mError = UUPErrorType.BAD_OTHER;
                            mItem.cancle();
                        }
                    }
                }else {
                    if(mItem.mCurrentItem != null){
                        mItem.mCurrentItem.isSuspend = false;
                        mItem.mCurrentItem.isFinish = false;
                        mItem.mCurrentItem.mPProgress = 0.0f;
                    }
                    mItem.preStart();
                }

            } else {
                mItem.mError = UUPErrorType.BAD_FUID;
            }
            urlConn.disconnect();
        } catch (Exception e) {
            mItem.mError = UUPErrorType.BAD_FUID;
            e.printStackTrace();
        }
        mItem.mHander.sendEmptyMessage(0);
    }

    private Map<String,String> excuteData(String jsonString) {
        try {
            if(jsonString == null || jsonString.length() <1)return null;
            Log.d("UUPItemFUID", "excuteData: "+ this);
            JSONObject jobj = new JSONObject(jsonString);
            Map<String,String> result = new HashMap<>();
            result.put("status",jobj.optString("status"));
            result.put("msg",jobj.optString("msg"));
            result.put("errorCode",jobj.optString("errorCode"));
            JSONObject data = jobj.getJSONObject("data");
            result.put("fuid",data.optString("fuid"));
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        getFUID();
    }
}
