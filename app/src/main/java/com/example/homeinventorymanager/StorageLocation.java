package com.example.homeinventorymanager;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 存放位置实体类（独立管理，如冰箱、化妆台、车库）
 */
@Entity(tableName = "storage_location")
public class StorageLocation {
    @PrimaryKey(autoGenerate = true)
    private int id; // 主键ID

    private String locationName; // 位置名称（如冰箱、化妆台）

    // 无参构造函数（Room 必需，Room 会使用该构造函数实例化对象）
    public StorageLocation() {
    }

    // 带参构造函数（手动创建位置对象时使用，添加 @Ignore 让 Room 忽略，消除警告）
    @Ignore
    public StorageLocation(String locationName) {
        this.locationName = locationName;
    }

    // getter & setter 方法（保留不变，无需修改）
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
}