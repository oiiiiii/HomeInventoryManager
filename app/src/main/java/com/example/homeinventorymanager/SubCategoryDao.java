package com.example.homeinventorymanager;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 子分类数据访问接口（支持按父分类查询）
 */
@Dao
public interface SubCategoryDao {
    // 新增子分类
    @Insert
    void insertSubCategory(SubCategory subCategory);

    // 根据父分类ID查询子分类（核心：实现分类联动，避免跨界）
    @Query("SELECT * FROM sub_category WHERE parentCategoryId = :parentId ORDER BY id ASC")
    List<SubCategory> querySubCategoryByParentId(int parentId);

    // 查询所有子分类（用于设置界面展示）
    @Query("SELECT * FROM sub_category ORDER BY parentCategoryId ASC")
    List<SubCategory> queryAllSubCategories();

    // 删除子分类
    @Delete
    void deleteSubCategory(SubCategory subCategory);

    // 根据父分类ID查询子分类总数
    @Query("SELECT COUNT(*) FROM sub_category WHERE parentCategoryId = :parentId")
    int getSubCategoryCountByParentId(int parentId);
}