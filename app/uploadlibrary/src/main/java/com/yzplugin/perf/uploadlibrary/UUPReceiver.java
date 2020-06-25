package com.yzplugin.perf.uploadlibrary;

import android.util.Log;

import net.gotev.uploadservice.UploadServiceBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

class UUPReceiver extends UploadServiceBroadcastReceiver {

    private UUPItem mItem;

    UUPReceiver(UUPItem item){
        mItem = item;
    }

    @Override
    public void onCancelled(String uploadId) {
        super.onCancelled(uploadId);
        Log.d("UUPItem", "onCancelled: "+ uploadId);
        if(!uploadId.equals(mItem.mRequestID))return;
        if (mItem.mCurrentItem == null) return;
        mItem.mCurrentItem.isSuspend = false;
        mItem.mProgress = mItem.mPProgress - mItem.mCurrentItem.mPProgress;
        mItem.mCurrentItem = null;
        if (mItem.mSliced.remainChunk()>0 && !mItem.isCancel){
            Log.d("UUPItem", "Faild Retry: "+ uploadId);
            mItem.next();
            return;
        }
        if(mItem.mDelegate.get() != null)
            mItem.mDelegate.get().onUPFaild(mItem);
    }

    @Override
    public void onCompleted(String uploadId, int serverResponseCode, byte[] serverResponseBody) {
        super.onCompleted(uploadId, serverResponseCode, serverResponseBody);
        if(!uploadId.equals(mItem.mRequestID))return;
        Log.d("UUPItem", "onCompleted: "+ mItem.mSliced.mTotalChunks + "--"+mItem.mSliced.remainChunk()+"   "+new String(serverResponseBody));
        if(!checkIsSessionValid(serverResponseBody)){
            Log.d("UUPItem", "onCancelled: "+ uploadId);
            mItem.cancle();
            if(mItem.mDelegate.get() != null)
                mItem.mDelegate.get().onUPFaild(mItem);
            return;
        }

        mItem.mPProgress += mItem.mCurrentItem.mProgress;
        mItem.mCurrentItem.isSuspend = false;
        mItem.mSliced.clean(mItem.mCurrentItem);
        mItem.mCurrentItem = null;

        String reomoteUri = getDescFileName(serverResponseBody);
        if(reomoteUri!=null)mItem.mRemoteUri =reomoteUri;

        if (mItem.mSliced.remainChunk()<1){
            mItem.isFinish = true;
            mItem.isStartting = false;
            mItem.mProgress = 1.0f;
            if(mItem.mDelegate.get() != null)
                mItem.mDelegate.get().onUPProgress(mItem);
            if(mItem.mDelegate.get() != null)
                mItem.mDelegate.get().onUPFinish(mItem);
            Log.d("UUPItem", "onFinish: "+ mItem);
            mItem.cancle();
        }
    }

    @Override
    public void onError(String uploadId, Exception exception) {
        super.onError(uploadId, exception);
        if(!uploadId.equals(mItem.mRequestID))return;
        Log.d("UUPItem", "onError: "+ mItem);
        if(mItem.mDelegate.get() != null) {
            mItem.mDelegate.get().onUPError(mItem);
        }
        if(!mItem.isFinish){
            mItem.pause();
        }
    }

    @Override
    public void onProgress(String uploadId, long uploadedBytes, long totalBytes) {
        super.onProgress(uploadId, uploadedBytes, totalBytes);
        if(!uploadId.equals(mItem.mRequestID))return;
        mItem.mCurrentItem.mPProgress = uploadedBytes * 1.0f/totalBytes * mItem.mCurrentItem.mProgress;
        mItem.mProgress = mItem.mPProgress + mItem.mCurrentItem.mPProgress;
        Log.d("UUPItem", "onProgress: "+ mItem.mSize+"--"+uploadedBytes+"--"+mItem.mProgress+"--"+mItem.mSpeed+"--"+mItem.mSpeedStr);
        if(mItem.mDelegate.get() != null)
            mItem.mDelegate.get().onUPProgress(mItem);
    }

    private boolean checkIsSessionValid(byte[] body) {
        try {
            String jsonString = new String(body);
            JSONObject jobj = new JSONObject(jsonString);
            String ret = jobj.optString("result");
            if (ret.equals("-1001"))
                return false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return true;
    }

    private String getDescFileName(byte[] body) {
        try {
            String jsonString = new String(body);
            JSONObject jobj = new JSONObject(jsonString);
            return jobj.optString("descfilename");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}