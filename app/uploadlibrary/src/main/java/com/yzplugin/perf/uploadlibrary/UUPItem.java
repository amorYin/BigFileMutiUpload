package com.yzplugin.perf.uploadlibrary;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.UploadService;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
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
    public float mProgress; //计算进度
    public long mSpeed;
    public String mSpeedStr;
    public String mSizeStr;
    public UUPItemType mType;
    public UUPErrorType mError;
    public boolean isFinish;
    ////////////////////////////
    protected UUPSlicedItem mCurrentItem;
    protected String mUploadFileName;
    protected Context mContext;
    protected MultipartUploadRequest request;
    protected UUPReceiver mReceiver;
    protected boolean isStartting;
    protected boolean isPaused;
    protected boolean isCancel;
    protected boolean isIgnoreCancel;
    protected boolean isValidate;
    protected boolean isChecked;
    protected float mPProgress = 0.0f; //分片上传真实进度
    protected String mRequestID;
    protected String mFUID;
    protected WeakReference<UUPItf> mDelegate;
    protected UUPConfig mConfig;
    protected UUPSliced mSliced;
    private float mLastProgress = 0.0f;
    private Timer mSpeedTimer;
    private int lowTimes = 0;

    WeakReference<UUPItem> weakReference = new WeakReference<>(this);
    final UUPItem weakSelf = weakReference.get();
    @SuppressLint("HandlerLeak")
    protected Handler mHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0){
                if(mError != UUPErrorType.NONE && mDelegate != null && mDelegate.get() != null)
                    mDelegate.get().onUPError(weakSelf);
            }else if(msg.what == 1){
                if(mDelegate != null && mDelegate.get() != null)
                    mDelegate.get().onUPProgress(weakSelf);
            }else if(msg.what == 2){
                if(mDelegate != null && mDelegate.get() != null)
                    mDelegate.get().onUPFinish(weakSelf);
                Log.d("UUPItem", "onUPFinish: "+weakSelf);
            }else {
                if(mDelegate != null && mDelegate.get() != null)
                    mDelegate.get().onUPStart(weakSelf);
            }

        }
    };

    public UUPItem(Context context, Uri path, UUPItemType type){
        mContext = context;
        mContentUri = path;
        mType = type;
        mSpeed = 0L;
        mProgress = 0.0f;
        mPProgress = 0.0f;
        mLastProgress = 0.0f;
        isValidate = true;
        isIgnoreCancel = false;
        mError = UUPErrorType.NONE;
        if(mContentUri != null){initSet();}
        else {isValidate = false;}
    }

    protected void setDelegate(UUPItf delegate){
        mDelegate = new WeakReference<>(delegate);
    }


    protected void start() {
        if(!this.isChecked){
            this.check();
        }

        if (this.isValidate) {
            isPaused = false;

            if (request != null){
                isIgnoreCancel = true;
                UploadService.stopUpload(mRequestID);
                mRequestID = null;
                request = null;
            }
            if (mFUID == null){
                getFUID();
            }else {
                this.next();
                this.calculateSpeed();
            }
            this.isStartting = true;
            mHander.sendEmptyMessage(4);
        }
    }

    public void pause(){
        Log.d("UUPItem", "pause-cancle: "+ this);
        if (isPaused)return;
        isPaused = true;
        if (request != null){
            isIgnoreCancel = true;
            UploadService.stopUpload(mRequestID);
            mRequestID = null;
            request = null;
        }
        destory();
    }

    public void cancle(){
        if (isCancel)return;
        Log.d("UUPItem", "cancle-cancle: "+ this);
        isCancel = true;
        destory();
    }

    protected void preStart(){
        if (isValidate){
            if(!isPaused){
                if(mSliced.remainChunk() < 1){
                    preFinish();
                }else {
                    if(request != null){
                        isIgnoreCancel = true;
                        UploadService.stopUpload(mRequestID);
                        request = null;
                    }
                    if(mFUID == null){
                        getFUID();
                    }else {
                        next();
                        isIgnoreCancel = false;
                    }
                }
            }
        }
    }

    protected void preFinish(){
        isFinish = true;
        mProgress = 1.0f;
        mHander.sendEmptyMessage(1);
        mHander.sendEmptyMessage(2);
        destory();
    }

    public void destory(){

        UUPUtil.deleteThumbnail(mThumbnailsPath);

        if(mSpeedTimer!=null){
            mSpeedTimer.cancel();
        }
        if( request != null){
            isIgnoreCancel = true;
            UploadService.stopUpload(mRequestID);
            request = null;
        }
        if(mReceiver!=null)
            mReceiver.unregister(mContext);

        if(mSliced!=null)
            mSliced.destroy();

        mRequestID = null;
        isPaused = false;
        mCurrentItem = null;
        mSliced = null;
        mReceiver = null;
        mSpeedTimer = null;
    }

    protected void getFUID(){
        WeakReference<UUPItem> item = new WeakReference<>(this);
        new Thread(new UUPItemFUID(item)).start();
    }

    protected void next(){
        //暂停的开始
        if(isPaused && request != null){
            try {
                mRequestID = request.startUpload();
                isPaused = false;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                mError = UUPErrorType.BAD_NET;
                mHander.sendEmptyMessage(0);
            }
        }
        //并行一个
        if(mCurrentItem!=null && !mCurrentItem.isFinish) return;
        mCurrentItem = mSliced.nextSliced();
        if (mCurrentItem == null) {
            isFinish = true;
            isStartting = false;
            mProgress = 1.0f;
            mHander.sendEmptyMessage(1);
            mHander.sendEmptyMessage(2);
            cancle();
            return;
        }
        if (request != null) {
            if(mRequestID != null)
                UploadService.stopUpload(mRequestID);
            request = null;
        }
        Log.d("UUPItem", "next: "+ mCurrentItem.mChunkIndex+" "+mCurrentItem.isSuspend);
        try {
            request = new MultipartUploadRequest(mContext,UUPUtil.randomName() + mDisplayName,mConfig.serverUri);
            request.addFileToUpload(mCurrentItem.mChunkFile.getAbsolutePath(), "filename");
        } catch (Exception e) {
            e.printStackTrace();
            mError = UUPErrorType.BAD_IO;
            mHander.sendEmptyMessage(0);
            return;
        }

//        request.addHeader("Auth-Sign", mConfig.authSign);
//        request.addParameter("card", mConfig.card);
//        request.addParameter("chunk", String.valueOf(mCurrentItem.mChunkIndex));
//        request.addParameter("chunks", String.valueOf(mSliced.mTotalChunks));
//        request.addParameter("name", mDisplayName);

        request.addHeader("auth-sign", mConfig.authSign);
        request.addParameter("fuid", mFUID);
        request.addParameter("index", String.valueOf(mCurrentItem.mChunkIndex+1));
        request.addParameter("total", String.valueOf(mSliced.mTotalChunks));
        request.addParameter("size", String.valueOf(mSize));
        request.setAutoDeleteFilesAfterSuccessfulUpload(false);
        request.setMaxRetries(mConfig.retryTimes);

        try {
            mRequestID = request.startUpload();
            isPaused = false;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            mError = UUPErrorType.BAD_NET;
            mHander.sendEmptyMessage(0);
        }
        Log.d("UUPItem", "start: "+ this);
    }

    private void initSet(){
        try {
            if("file".equals(mContentUri.getScheme())){

                if(mContentUri.getPath()!=null) {
                    File mFile = new File(mContentUri.getPath());
                    if (mFile.exists()) {
                        if (mType == UUPItemType.VIDEO) {
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(mContext, mContentUri);
                            long rd = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                            mDuration = Math.round(rd * 1.0 /1000);
                            mThumbnailsPath = UUPUtil.getThumbnailsPath(mContext, mFile, mmr.getFrameAtTime(0));
                            mMimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                            mmr.release();
                        } else if (mType == UUPItemType.AUDIO) {
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(mContext, mContentUri);
                            long rd = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                            mDuration = Math.round(rd * 1.0 /1000);
                            mThumbnailsPath = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                            mMimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                            mmr.release();
                        } else {
                            mThumbnailsPath = mFile.getAbsolutePath();
                            mMimeType = UUPUtil.getMimeType(mFile);
                        }
                        mFilePath = mFile.getAbsolutePath();
                        mDisplayName = mFile.getName();
                        mSize = mFile.length();
                    }
                }
            }else if("content".equals(mContentUri.getScheme())){
                String[] projection = {
                        MediaStore.MediaColumns.DATA
                        ,MediaStore.MediaColumns.SIZE
                        ,MediaStore.MediaColumns.DISPLAY_NAME
                        ,MediaStore.MediaColumns.MIME_TYPE };

//                Cursor cursor = null;
//                String selection = MediaStore.MediaColumns.DATA+" like ?";
//                String[] selectionArgs = {displayName()};
                ContentResolver contentResolver = mContext.getContentResolver();

                Cursor cursor = contentResolver.query(mContentUri, projection, null, null, null);

                if(cursor != null && cursor.moveToFirst()){
                    int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
                    mFilePath = cursor.getString(columnIndex);
                    Log.d("UUPItem: ",mFilePath);

                    columnIndex = cursor.getColumnIndexOrThrow(projection[1]);
                    mSize = cursor.getInt(columnIndex);

                    columnIndex = cursor.getColumnIndexOrThrow(projection[2]);
                    mDisplayName = ""+cursor.getString(columnIndex);

                    columnIndex = cursor.getColumnIndexOrThrow(projection[3]);
                    mMimeType = cursor.getString(columnIndex);
                }
                if(cursor!=null)cursor.close();
                if (mFilePath != null && mType != UUPItemType.IMAGE){
                    File mFile = new File(mFilePath);
                    if (mType == UUPItemType.VIDEO){
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(mContext,mContentUri);
                        long rd = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        mDuration = Math.round(rd * 1.0 /1000);
                        mThumbnailsPath = UUPUtil.getThumbnailsPath(mContext,mFile,mmr.getFrameAtTime(0));
                        mmr.release();
                    }else if(mType == UUPItemType.AUDIO){
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(mContext,mContentUri);
                        long rd = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        mDuration = Math.round(rd * 1.0 /1000);
                        mThumbnailsPath = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        mmr.release();
                    }
                }
            }else {
                isValidate = false;
                mError = UUPErrorType.BAD_FILE;
            }
        }catch (Exception e){
            e.printStackTrace();
            mError = UUPErrorType.BAD_IO;
        }

        if(mFilePath == null || mSize < 1) {
            isValidate = false;
        } else{
            isValidate = true;
            mUploadFileName = UUPUtil.randomName() + mDisplayName;
            mSizeStr = UUPUtil.calculateSize(mSize);
        }
        mHander.sendEmptyMessage(0);

        Log.d("UUPItem: ","mDisplayName:"+mDisplayName+" mMimeType:"+mMimeType+" mDuration:"+mDuration+" mSize:"+mSize+" mFilePath:"+mFilePath+" mThumbnailsPath:"+mThumbnailsPath);
    }

    protected void check(){
        if(mDelegate == null)
            return;

        if (mConfig == null)
            mConfig = mDelegate.get().onConfigure();

        if (mSliced == null){
            mSliced = new UUPSliced(new WeakReference<>(this));
            if(isValidate)mSliced.makeChunks();
        }

        if(mReceiver == null){
            mReceiver = new UUPReceiver(this);
            mReceiver.register(mContext);
        }

        if(mSize > mConfig.size){
            mError = UUPErrorType.OVER_MAXSIZE;
            isValidate = false;
        } else if(mDuration > mConfig.duration){
            mError = UUPErrorType.OVER_MAXDURATION;
            isValidate = false;
        }

        if(mError != UUPErrorType.NONE ){
            mHander.sendEmptyMessage(0);
        }
        isChecked = true;
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
                    if(mSpeed < 0)mSpeed = mSpeed * -1;
                    mSpeedStr = UUPUtil.calculateSpeed(mSpeed);
                    mLastProgress = mProgress;
                    Log.d("UUPItem", "calculateSpeed: "+ mSpeedStr +" "+mSpeed +" "+tmp);
                    if(lowTimes >= 10 && lowTimes % 5 == 0)//网速缓慢每间隔5秒提示一次
                    { mError = UUPErrorType.LOW_NET;mHander.sendEmptyMessage(0);}
                    mHander.sendEmptyMessage(1);
                    if(!UUPUtil.isNetworkConnected(mContext)){ pause(); }else { preStart(); }
                    if(mSpeed < 10){ lowTimes++; }else { lowTimes = 0; }
                }
            },0,1000);
        }
    }

    //仅供recevier使用
    protected void pCalculateSpeed(){
        double tmp = (mProgress - mLastProgress) * mSize;
        mSpeed = (long) tmp;
        if(mSpeed < 0)mSpeed = mSpeed * -1;
        mSpeedStr = UUPUtil.calculateSpeed(mSpeed);
        mLastProgress = mProgress;
        Log.d("UUPItem", "calculateSpeed: "+ mSpeedStr +" "+mSpeed +" "+tmp);
        mHander.sendEmptyMessage(1);
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