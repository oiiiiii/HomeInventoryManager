package com.example.homeinventorymanager;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 存放位置数据访问接口
 */
@Dao
public interface StorageLocationDao {
    // 新增存放位置
    @Insert
    void insertStorageLocation(StorageLocation storageLocation);

    // 查询所有存放位置（按ID升序）
    @Query("SELECT * FROM storage_location ORDER BY id ASC")
    List<StorageLocation> queryAllStorageLocations();

    // 删除存放位置
    @Delete
    void deleteStorageLocation(StorageLocation storageLocation);

    // 查询位置总数
    @Query("SELECT COUNT(*) FROM storage_location")
    int getStorageLocationCount();
}