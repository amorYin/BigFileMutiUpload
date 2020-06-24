package com.yzplugin.perf.uploadlibrary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.UploadService;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class UUPItem {

    public String mRemoteUri; //上传后返回的地址
    public Uri mContentUri;
    public String mDisplayName;
    public String mFilePath;
    public String mThumbnailsPath;
    public String mMimeType;
    public long mDuration;
    public long mSize;
    public float mProgress = 0.0f; //计算进度
    public long mSpeed = 0;
    public String mSpeedStr;
    public UUPItemType mType;


    ////////////////////////////
    protected UUPSlicedItem mCurrentItem;
    protected Context mContext;
    protected MultipartUploadRequest request;
    protected UUPReceiver mReceiver;
    protected boolean isStartting;
    protected boolean isPaused;
    protected boolean isFinish;
    protected boolean isValidate = true;
    protected float mPProgress = 0.0f; //分片上传真实进度
    protected String mRequestID;
    protected WeakReference<UUPItf> mDelegate;
    protected UUPConfig mConfig;
    protected UUPSliced mSliced;
    private float mLastProgress = 0.0f;
    private Timer mSpeedTimer;

    public UUPItem(Context context, Uri path, UUPItemType type){
        mContext = context;
        mContentUri = path;
        mType = type;
        if(mContentUri != null){initSet();}
        else {isValidate = false;}
    }

    private void initSet(){
            String[] projection = {
                    MediaStore.Video.Media.DATA
                    ,MediaStore.Video.Thumbnails.DATA
                    ,MediaStore.Video.Media.DURATION
                    ,MediaStore.Video.Media.SIZE
                    ,MediaStore.Video.Media.DISPLAY_NAME
                    ,MediaStore.Video.Media.MIME_TYPE };

        String selection = MediaStore.Video.Media.DATA+" like ?";
        String[] selectionArgs = {displayName()};

        @SuppressLint("Recycle")
        Cursor cursor = mContext.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
        if(cursor != null && cursor.moveToFirst()){
            int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
            mFilePath = cursor.getString(columnIndex);
            Log.d("UUPItem: ",mFilePath);

            columnIndex = cursor.getColumnIndexOrThrow(projection[1]);
            mThumbnailsPath = cursor.getString(columnIndex);

            columnIndex = cursor.getColumnIndexOrThrow(projection[2]);
            mDuration = cursor.getInt(columnIndex);

            columnIndex = cursor.getColumnIndexOrThrow(projection[3]);
            mSize = cursor.getInt(columnIndex);

            columnIndex = cursor.getColumnIndexOrThrow(projection[4]);
            mDisplayName = cursor.getString(columnIndex);

            columnIndex = cursor.getColumnIndexOrThrow(projection[5]);
            mMimeType = cursor.getString(columnIndex);
        }
//        while (cursor!=null && cursor.moveToNext()){
//            int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
//            String path = cursor.getString(columnIndex);
//            Log.d("UUPItem: ",path);
//
//        }

        if(mFilePath == null) {isValidate = false;}
        Log.d("UUPItem: ","mDisplayName:"+mDisplayName+" mMimeType:"+mMimeType+" mDuration:"+mDuration+" mSize:"+mSize+" mFilePath:"+mFilePath+" mThumbnailsPath:"+mThumbnailsPath);
    }

    private String displayName(){
        String file = mContentUri.getPath();
        if (file == null) return "*";
        String[] paths = file.split("/");
        return "%"+paths[paths.length - 1];
    }

    protected void setDelegate(UUPItf delegate){
        mDelegate = new WeakReference<>(delegate);
    }

    protected void check(){
        if(mDelegate == null)
            return;

        if (mConfig == null){
            mConfig = mDelegate.get().onConfigure();
        }

        if (mSliced == null){
            mSliced = new UUPSliced(new WeakReference<>(this));
            if(isValidate)mSliced.makeChunks();
        }

        if(mReceiver == null){
            mReceiver = new UUPReceiver(this);
            mReceiver.register(mContext);
        }
        if(mSize > mConfig.size){isValidate = false;}
    }

    protected void start() {
        isStartting = true;
        if(!isPaused)check();
        //check
        if(!isValidate)return;
        next();
        calculateSpeed();
    }

    protected void next(){
        //暂停的开始
        if(isPaused && request != null){
            try {
                mRequestID = request.startUpload();
                isPaused = false;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                if(mDelegate.get() != null){
                    mDelegate.get().onUPError(this);
                }
            }
        }
        //并行一个
        if(mCurrentItem!=null && !mCurrentItem.isFinish) return;
        mCurrentItem = mSliced.nextSliced();
        if (mCurrentItem == null) return;
        if (request != null) request = null;
        Log.d("UUPItem", "next: "+ mCurrentItem.mChunkIndex+" "+mCurrentItem.isSuspend);
        request = new MultipartUploadRequest(mContext,(new Date()).getTime() +"-"+ mDisplayName,mConfig.serverUri);
        try {
            request.addFileToUpload(mCurrentItem.mChunkFile.getAbsolutePath(), "file");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mDelegate.get().onUPError(this);
            return;
        }

        request.addHeader("Auth-Sign", mConfig.authSign);
        request.addParameter("card", mConfig.card);
        request.addParameter("chunk", String.valueOf(mCurrentItem.mChunkIndex));
        request.addParameter("chunks", String.valueOf(mSliced.mTotalChunks));
        request.addParameter("name", mDisplayName);
        request.setAutoDeleteFilesAfterSuccessfulUpload(false);
        request.setMaxRetries(mConfig.retryTimes);


        try {
            mRequestID = request.startUpload();
            isPaused = false;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            if(mDelegate.get() != null){
                mDelegate.get().onUPError(this);
            }
        }
        Log.d("UUPItem", "start: "+ this);
    }

    protected void pause(){
        Log.d("UUPItem", "pause-cancle: "+ this);
        if (request != null && !isFinish && !isPaused && mRequestID != null){
            UploadService.stopUpload(mRequestID);
            isPaused = true;
            isStartting = false;
        }
    }

    protected void cancle(){
        Log.d("UUPItem", "cancle-cancle: "+ this);
        if( mRequestID != null){
            UploadService.stopUpload(mRequestID);
        }
        if (request != null){
            request = null;
        }
        mRequestID = null;
        isPaused = false;
        mCurrentItem = null;
        if(mSliced!=null)mSliced.destroy();
        mSliced = null;
        if(mReceiver!=null)mReceiver.unregister(mContext);
        mReceiver = null;
        if(mSpeedTimer!=null){
            mSpeedTimer.cancel();
            mSpeedTimer.purge();
        }
        mSpeedTimer = null;
    }

    private void calculateSpeed(){
        if (mSpeedTimer == null){
            mSpeedTimer = new Timer();
            mSpeedTimer.schedule(new TimerTask() {
                @SuppressLint("DefaultLocale")
                @Override
                public void run() {
                    double tmp = (mProgress - mLastProgress) * mSize;
                    mSpeed = (long) tmp;
                    if (mSpeed > 1024 * 1024 ){
                        mSpeedStr = String.format("%.2fMB/s",mSpeed*1.0/1024/1024);
                    }else if(mSpeed > 1024){
                        mSpeedStr = String.format("%.1fKB/s",mSpeed*1.0/1024);
                    }else {
                        mSpeedStr = String.format("%.0fB/s",mSpeed*1.0);
                    }
                    mLastProgress = mProgress;
                    Log.d("UUPItem", "calculateSpeed: "+ mSpeedStr +" "+mSpeed +" "+tmp);
                }
            },0,1000);
        }
    }


    @Override
    public String toString() {
        return "UUPItem{" +
                "mRemoteUri='" + mRemoteUri + '\'' +
                ", mContentUri=" + mContentUri +
                ", mDisplayName='" + mDisplayName + '\'' +
                ", mFilePath='" + mFilePath + '\'' +
                ", mThumbnailsPath='" + mThumbnailsPath + '\'' +
                ", mMimeType='" + mMimeType + '\'' +
                ", mDuration=" + mDuration +
                ", mSize=" + mSize +
                ", mProgress=" + mProgress +
                ", mSpeed=" + mSpeed +
                ", mSpeedStr='" + mSpeedStr + '\'' +
                ", mType=" + mType +
                ", mContext=" + mContext +
                '}';
    }
}

