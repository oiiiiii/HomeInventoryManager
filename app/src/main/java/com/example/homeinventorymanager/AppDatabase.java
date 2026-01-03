package com.example.homeinventorymanager;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import com.example.homeinventorymanager.bean.ItemImage; // 新增：导入ItemImage实体类
import com.example.homeinventorymanager.dao.ItemImageDao; // 新增：导入ItemImageDao接口

/**
 * 升级后的Room数据库（移除主线程查询，支持异步操作）
 * 新增：支持物品图片关联表（ItemImage）
 */
@Database(entities = {Item.class, Category.class, SubCategory.class, StorageLocation.class, ItemImage.class}, // 新增：添加ItemImage.class到实体数组
        version = 2,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "item_database";

    private static AppDatabase INSTANCE;

    public abstract CategoryDao categoryDao();
    public abstract SubCategoryDao subCategoryDao();
    public abstract StorageLocationDao storageLocationDao();
    public abstract ItemDao itemDao();
    public abstract ItemImageDao itemImageDao(); // 新增：声明ItemImageDao方法（核心修复）

    public static synchronized AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    // 关键：删除 allowMainThreadQueries() 这一行，禁用主线程数据库操作
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return INSTANCE;
    }
}