package com.yzplugin.perf.uploadlibrary;

import android.util.Log;

import net.gotev.uploadservice.UploadServiceBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

class UUPReceiver extends UploadServiceBroadcastReceiver {

    private UUPItem mItem;

    UUPReceiver(UUPItem item){
        mItem = item;
    }

    @Override
    public void onCancelled(String uploadId) {
        super.onCancelled(uploadId);
        if(mItem == null || !uploadId.equals(mItem.mRequestID))return;
        Log.d("UUPItem", "onCancelled: "+ uploadId+"|"+mItem.mError);
        if (mItem.mCurrentItem != null) {
            mItem.mCurrentItem.isSuspend = false;
            mItem.mCurrentItem = null;
            if(!mItem.isCancel && !mItem.isIgnoreCancel){
                if (UUPUtil.isNetworkConnected(mItem.mContext)){
                    mItem.preStart();
                }else {
                    mItem.mError = UUPErrorType.BAD_NET;
                    if(UUPUtil.isNetworkConnected(mItem.mContext)){
                        mItem.preStart();
                    }else {
                        mItem.pause();
                    }
                }
            }
        }
        mItem.mHander.sendEmptyMessage(0);
    }

    @Override
    public void onCompleted(String uploadId, int serverResponseCode, byte[] serverResponseBody) {
        super.onCompleted(uploadId, serverResponseCode, serverResponseBody);
        if(mItem == null || !uploadId.equals(mItem.mRequestID))return;
        Log.d("UUPItem", "onCompleted: "+ mItem.mSliced.mTotalChunks + "--"+mItem.mSliced.remainChunk()+"   "+new String(serverResponseBody));

        Map<String,String> result = checkIsSessionValid(serverResponseBody);
        if(result != null){
            String code = result.get("errorCode");
            String status = result.get("status");
            if(code == null)code = "";
            if("FAIL".equals(status)){
                Log.d("UUPItem", "onError: "+ uploadId);
                switch (code) {
                    case "-1":
                    case "1002":
                        mItem.mError = UUPErrorType.BAD_FUID;
                        break;
                    case "-1001":
                    case "1000":
                        mItem.mError = UUPErrorType.BAD_ACCESS;
                        break;
                    case "-2":
                    case "1001":
                        mItem.mError = UUPErrorType.BAD_PARAMS;
                        break;
                    case "1003":
                        mItem.mError = UUPErrorType.BAD_SLICED;
                        break;
                    default:
                        mItem.mError = UUPErrorType.BAD_OTHER;
                        break;
                }
                if(!mItem.isCancel){
                    mItem.mCurrentItem.isSuspend = false;
                    mItem.mCurrentItem.isFinish = false;
                    mItem.mCurrentItem.mPProgress = 0.0f;
                    mItem.mCurrentItem = null;
                    if(mItem.mError == UUPErrorType.BAD_FUID){
                        mItem.getFUID();
                    }else {
                        mItem.preStart();
                    }
                }
            }else {
                String current_index = result.get("current_index");
                if(current_index == null)current_index = "-1";
                String save_index = result.get("save_index");
                if(save_index == null)current_index = "0";
                String file_path = result.get("file_path");
                if(current_index.equals(save_index) || file_path != null){
                    mItem.mCurrentItem.isFinish = true;
                    mItem.mPProgress += mItem.mCurrentItem.mProgress;
                    if(file_path != null) mItem.mRemoteUri = file_path;
                    mItem.mSliced.clean(mItem.mCurrentItem);
                    mItem.mCurrentItem = null;
                }else {
                    mItem.mCurrentItem.isSuspend = false;
                    mItem.mCurrentItem.isFinish = false;
                    mItem.mCurrentItem.mPProgress = 0.0f;
                    mItem.mCurrentItem = null;
                }
                mItem.mError = UUPErrorType.NONE;
                mItem.preStart();
            }
        }else {
            mItem.mCurrentItem.isSuspend = false;
            mItem.mCurrentItem.isFinish = false;
            mItem.mCurrentItem.mPProgress = 0.0f;
            mItem.mCurrentItem = null;
            mItem.preStart();
        }
        mItem.mHander.sendEmptyMessage(0);
    }

    @Override
    public void onError(String uploadId, Exception exception) {
        super.onError(uploadId, exception);
        if(mItem == null || !uploadId.equals(mItem.mRequestID))return;
        Log.d("UUPItem", "onError: "+ mItem);
        if (mItem.mCurrentItem != null) {
            mItem.mCurrentItem.isSuspend = false;
            mItem.mCurrentItem.isFinish = false;
            mItem.mCurrentItem.mPProgress = 0.0f;
            mItem.mCurrentItem = null;
        }
        if(!mItem.isCancel){
            mItem.isIgnoreCancel = false;
            mItem.preStart();
        }
        mItem.mHander.sendEmptyMessage(0);
    }

    @Override
    public void onProgress(String uploadId, long uploadedBytes, long totalBytes) {
        super.onProgress(uploadId, uploadedBytes, totalBytes);
        if(!uploadId.equals(mItem.mRequestID))return;
        mItem.mCurrentItem.mPProgress = uploadedBytes * 1.0f/totalBytes * mItem.mCurrentItem.mProgress;
        mItem.mProgress = mItem.mPProgress + mItem.mCurrentItem.mPProgress;
        Log.d("UUPItem", "onProgress: "+ mItem.mSize+"--"+uploadedBytes+"--"+mItem.mProgress+"--"+mItem.mSpeed+"--"+mItem.mSpeedStr);
        mItem.mHander.sendEmptyMessage(1);
    }

    private Map<String,String> checkIsSessionValid(byte[] body) {
        try {
            if(body.length <1)return null;
            String jsonString = new String(body);
            JSONObject jobj = new JSONObject(jsonString);
            Map<String,String> result = new HashMap<>();
            result.put("status",jobj.optString("status"));
            result.put("msg",jobj.optString("msg"));
            result.put("errorCode",jobj.optString("errorCode"));
            JSONObject data = jobj.optJSONObject("data");
            if(data != null){
                result.put("file_path",data.optString("file_path"));
                result.put("current_index",data.optString("current_index"));
                result.put("save_index",data.optString("save_index"));
            }
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Map<String,String> getDescFileName(byte[] body) {
        try {
            if(body.length <1)return null;
            Map<String,String> result = new HashMap<>();
            String jsonString = new String(body);
            JSONObject jobj = new JSONObject(jsonString);
            String filename = jobj.optString("descfilename");
            result.put("descfilename",filename);
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}