package com.example.homeinventorymanager;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 分类数据访问接口
 */
@Dao
public interface CategoryDao {
    // 新增分类
    @Insert
    void insertCategory(Category category);

    // 查询所有分类（按ID升序）
    @Query("SELECT * FROM category ORDER BY id ASC")
    List<Category> queryAllCategories();

    // 根据ID查询分类
    @Query("SELECT * FROM category WHERE id = :categoryId")
    Category queryCategoryById(int categoryId);

    // 删除分类
    @Delete
    void deleteCategory(Category category);

    // 查询分类总数
    @Query("SELECT COUNT(*) FROM category")
    int getCategoryCount();
}