package com.example.homeinventorymanager.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageUtils {
    // 照片保存目录（应用私有目录，无需额外权限）
    private static final String PHOTO_DIR = "HomeInventory/Photos";
    // 时间戳格式，用于生成唯一照片名称
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.CHINA);
    // 全局变量：保存当前拍摄照片的文件路径
    public static String currentPhotoPath;

    /**
     * 获取相机拍摄的照片Uri（适配Android 7.0+ FileProvider，优化目录创建）
     */
    public static Uri getCameraPhotoUri(Context context) {
        File photoFile = null;
        try {
            photoFile = createPhotoFile(context);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        if (photoFile == null) {
            return null;
        }
        // 保存当前照片路径
        currentPhotoPath = photoFile.getAbsolutePath();
        // 通过FileProvider获取Uri，适配7.0+，添加异常捕获
        try {
            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider", // 与AndroidManifest中配置一致
                    photoFile
            );
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            // 低版本降级使用Uri.fromFile（兼容Android 7.0以下）
            return Uri.fromFile(photoFile);
        }
    }

    /**
     * 创建拍摄照片的文件（优化：优先使用应用私有目录，避免权限问题，抛出异常便于排查）
     */
    private static File createPhotoFile(Context context) throws IOException {
        // 生成唯一文件名
        String photoFileName = "IMG_" + sdf.format(new Date()) + ".jpg";
        File storageDir;

        // 优先使用应用私有外部存储目录（无需用户授权）
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            storageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), PHOTO_DIR);
        } else {
            // 外部存储不可用时，使用应用内部存储目录
            storageDir = new File(context.getFilesDir(), PHOTO_DIR);
        }

        // 递归创建目录（mkdirs()支持多级目录创建，比mkdir()更可靠）
        if (!storageDir.exists()) {
            boolean isDirCreated = storageDir.mkdirs();
            if (!isDirCreated) {
                // 内部存储兜底
                storageDir = new File(context.getCacheDir(), PHOTO_DIR);
                if (!storageDir.mkdirs()) {
                    throw new IOException("无法创建照片存储目录");
                }
            }
        }

        // 创建临时文件（确保文件唯一性，避免覆盖）
        File photoFile = File.createTempFile(
                photoFileName.substring(0, photoFileName.lastIndexOf(".")), // 文件名前缀
                ".jpg", // 文件后缀
                storageDir // 存储目录
        );

        // 确保文件可读写
        if (!photoFile.canWrite()) {
            photoFile.setWritable(true);
        }

        return photoFile;
    }

    /**
     * 启动图库选择图片（优化：添加Intent标志，提升兼容性）
     */
    public static Intent getGalleryIntent() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        // 添加标志，提升兼容性
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return galleryIntent;
    }

    /**
     * 启动相机拍摄图片（核心优化：修复未检测到相机应用问题，添加权限标志）
     */
    public static Intent getCameraIntent(Context context) {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 添加权限标志，允许相机应用访问照片Uri
        cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        // 确保有相机应用可以处理该意图（增加非空校验，避免返回空Intent）
        if (cameraIntent.resolveActivity(context.getPackageManager()) != null) {
            Uri photoUri = getCameraPhotoUri(context);
            if (photoUri != null) {
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                // 添加图片质量配置，提升兼容性
                cameraIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                cameraIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1024 * 1024 * 10); // 限制10MB内
            }
        }

        return cameraIntent;
    }
}