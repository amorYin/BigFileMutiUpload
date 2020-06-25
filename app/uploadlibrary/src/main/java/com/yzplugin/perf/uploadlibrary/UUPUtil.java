package com.yzplugin.perf.uploadlibrary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.MimeTypeMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Locale;

class UUPUtil {
    protected static String getSuffix(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return null;
        }
        String fileName = file.getName();
        if (fileName.equals("") || fileName.endsWith(".")) {
            return null;
        }
        int index = fileName.lastIndexOf(".");
        if (index != -1) {
            return fileName.substring(index + 1).toLowerCase(Locale.US);
        } else {
            return null;
        }
    }

    protected static String getMimeType(File file){
        String suffix = getSuffix(file);
        if (suffix == null) {
            return "file/*";
        }
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix);
        if (type != null && !type.isEmpty()) {
            return type;
        }
        return "file/*";
    }

    protected static void isFilesExist(File file){
        if (file != null && !file.exists()){
            //noinspection ResultOfMethodCallIgnored
            file.mkdir();
        }
    }

    protected static String randomName(){
        return (new Date()).getTime() +"-";
    }

    protected static String getThumbnailsPath(Context context, File file, Bitmap bm){
        try {
            File thumbnails = context.getExternalFilesDir("Thumbnails");
            UUPUtil.isFilesExist(thumbnails);
            String path = file.getName().replace(".","~");
            if(thumbnails != null){
                String filePath = thumbnails.getAbsolutePath()+"/"+path+".jpg";
                File chunkFile = new File(filePath);
                BufferedOutputStream bos = null;
                bos = new BufferedOutputStream(new FileOutputStream(chunkFile));
                bm.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                bos.flush();
                bos.close();
                return filePath;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressLint("DefaultLocale")
    protected static String calculateSpeed(double mSpeed) {
        String mSpeedStr = "0B/s";
        if (mSpeed > 1024 * 1024 ){
            mSpeedStr = String.format("%.2fMB/s",mSpeed*1.0/1024/1024);
        }else if(mSpeed > 1024){
            mSpeedStr = String.format("%.1fKB/s",mSpeed*1.0/1024);
        }else {
            mSpeedStr = String.format("%.0fB/s",mSpeed*1.0);
        }
        return mSpeedStr;
    }

    @SuppressLint("DefaultLocale")
    protected static String calculateSize(long mSize) {
        String mSizeStr = "0B";
        if(mSize > 1024L * 1024 * 1024){
            mSizeStr = String.format("%.2fGB",mSize*1.0/1024/1024/1024);
        }else if (mSize > 1024L * 1024 ){
            mSizeStr = String.format("%.2fMB",mSize*1.0/1024/1024);
        }else if(mSize > 1024L){
            mSizeStr = String.format("%.1fKB",mSize*1.0/1024);
        }else {
            mSizeStr = String.format("%.0fB",mSize*1.0);
        }
        return mSizeStr;
    }

}
