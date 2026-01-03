package com.example.homeinventorymanager;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.homeinventorymanager.utils.ImageUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 物品编辑页面（已修复：异步操作、主线程阻塞、空指针、Spinner匹配问题）
 * 支持动态加载自定义分类/子分类/位置，分类-子分类联动，异步更新数据库
 * 新增：图片拍摄+图库选择、修改页面显示已保存图片功能
 */
public class ItemEditActivity extends AppCompatActivity {

    // 原有控件声明
    private EditText etEditName, etEditCount, etEditValidDate, etEditDesc;
    private Spinner spEditCategory, spEditSubCategory, spEditLocation;

    // 新增：图片相关控件
    private LinearLayout llImageContainer; // 图片预览容器
    private View btnAddImage; // 添加图片按钮

    // 新增：图片请求码
    private static final int REQUEST_CODE_CAMERA = 1001;
    private static final int REQUEST_CODE_GALLERY = 1002;

    // 新增：图片路径列表（存储当前物品的所有图片路径）
    private List<String> imagePathList = new ArrayList<>();

    // 原有动态数据列表
    private List<Category> categoryList;
    private List<SubCategory> subCategoryList;
    private List<StorageLocation> storageLocationList;

    // 原有动态适配器
    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> subCategoryAdapter;
    private ArrayAdapter<String> locationAdapter;

    // 原有要编辑的物品ID和对象
    private int itemId;
    private Item editItem;

    // 原有兜底默认数据
    private String[] defaultCategories = {"食品", "日用品", "家电", "服饰", "其他"};
    private String[] defaultSubCategories = {"零食", "生鲜", "调味品", "清洁用品", "洗漱用品", "其他"};
    private String[] defaultLocations = {"冰箱", "厨房橱柜", "卫生间", "卧室衣柜", "客厅书架", "阳台", "其他"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_edit);

        // 1. 获取传递过来的物品ID
        Intent intent = getIntent();
        if (intent != null) {
            itemId = intent.getIntExtra("ITEM_ID", -1);
        }

        // 2. 校验物品ID有效性
        if (itemId == -1) {
            Toast.makeText(this, "获取物品信息失败！", Toast.LENGTH_SHORT).show();
            finish(); // 关闭当前页面
            return;
        }

        // 3. 初始化控件（新增：图片相关控件）
        initViews();

        // 4. 初始化下拉列表（原有逻辑，保持不变）
        initSpinners();

        // 5. 异步加载要编辑的物品数据（原有逻辑，保持不变）
        loadItemDataAsync();

        // 6. 绑定有效期输入框点击事件（原有逻辑，保持不变）
        bindValidDateClickListener();

        // 7. 绑定保存修改按钮点击事件（原有逻辑，保持不变）
        bindSaveEditClickListener();

        // 新增：8. 绑定添加图片按钮点击事件 + 初始化图片相关逻辑
        bindAddImageClickListener();
    }

    /**
     * 初始化控件（新增：图片容器和添加图片按钮）
     */
    private void initViews() {
        // 原有控件初始化
        etEditName = findViewById(R.id.et_edit_name);
        etEditCount = findViewById(R.id.et_edit_count);
        etEditValidDate = findViewById(R.id.et_edit_valid_date);
        etEditDesc = findViewById(R.id.et_edit_desc);
        spEditCategory = findViewById(R.id.sp_edit_category);
        spEditSubCategory = findViewById(R.id.sp_edit_sub_category);
        spEditLocation = findViewById(R.id.sp_edit_location);

        // 新增：图片相关控件初始化
        llImageContainer = findViewById(R.id.ll_image_container);
        btnAddImage = findViewById(R.id.btn_add_image);
    }

    /**
     * 原有逻辑：初始化下拉列表（动态加载+分类-子分类联动），保持不变
     */
    private void initSpinners() {
        // 初始化动态数据列表
        categoryList = new ArrayList<>();
        subCategoryList = new ArrayList<>();
        storageLocationList = new ArrayList<>();

        // 1. 分类Spinner适配器（动态）
        categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEditCategory.setAdapter(categoryAdapter);

        // 2. 子分类Spinner适配器（动态）
        subCategoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        subCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEditSubCategory.setAdapter(subCategoryAdapter);

        // 3. 位置Spinner适配器（动态）
        locationAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEditLocation.setAdapter(locationAdapter);

        // 4. 分类选择联动子分类
        spEditCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (categoryList.isEmpty()) {
                    return;
                }
                // 获取选中的父分类
                Category selectedCategory = categoryList.get(position);
                // 刷新子分类列表（仅展示该父分类下的子分类）
                refreshSubCategoryByParentId(selectedCategory.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 5. 异步加载自定义数据（从数据库读取，避免主线程阻塞）
        loadCustomDataFromDbAsync();
    }

    /**
     * 原有逻辑：异步加载自定义分类/子分类/位置数据，保持不变
     */
    private void loadCustomDataFromDbAsync() {
        new DbAsyncTask<Void, Void, Void>(getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Void>() {
                    @Override
                    public void onDbOperationCompleted(Void aVoid) {
                        // 主线程刷新Spinner（UI操作必须在主线程）
                        refreshCategorySpinner();
                        refreshLocationSpinner();

                        // 初始化子分类（默认选中第一个分类的子分类）
                        if (!categoryList.isEmpty()) {
                            refreshSubCategoryByParentId(categoryList.get(0).getId());

                            // 若物品数据已加载，重新匹配Spinner选中状态
                            if (editItem != null) {
                                setSpinnerSelection(spEditCategory, editItem.getCategory(), categoryAdapter);
                                setSpinnerSelection(spEditSubCategory, editItem.getSubCategory(), subCategoryAdapter);
                                setSpinnerSelection(spEditLocation, editItem.getLocation(), locationAdapter);
                            }
                        }
                    }
                }) {
            @Override
            protected Void doInBackground(Void... voids) {
                // 子线程执行数据库查询，不阻塞主线程
                AppDatabase db = AppDatabase.getInstance(mContext);

                // 1. 加载分类
                categoryList.clear();
                List<Category> dbCategories = db.categoryDao().queryAllCategories();
                if (dbCategories != null && !dbCategories.isEmpty()) {
                    categoryList.addAll(dbCategories);
                } else {
                    // 兜底默认分类
                    for (String name : defaultCategories) {
                        categoryList.add(new Category(name));
                    }
                }

                // 2. 加载所有子分类（用于联动筛选）
                subCategoryList.clear();
                List<SubCategory> dbSubCategories = db.subCategoryDao().queryAllSubCategories();
                if (dbSubCategories != null && !dbSubCategories.isEmpty()) {
                    subCategoryList.addAll(dbSubCategories);
                } else {
                    // 兜底默认子分类
                    if (!categoryList.isEmpty()) {
                        Category defaultParent = categoryList.get(0);
                        for (String name : defaultSubCategories) {
                            subCategoryList.add(new SubCategory(name, defaultParent.getId(), defaultParent.getCategoryName()));
                        }
                    }
                }

                // 3. 加载存放位置
                storageLocationList.clear();
                List<StorageLocation> dbLocations = db.storageLocationDao().queryAllStorageLocations();
                if (dbLocations != null && !dbLocations.isEmpty()) {
                    storageLocationList.addAll(dbLocations);
                } else {
                    // 兜底默认位置
                    for (String name : defaultLocations) {
                        storageLocationList.add(new StorageLocation(name));
                    }
                }
                return null;
            }
        }.execute();
    }

    /**
     * 原有逻辑：刷新分类Spinner，保持不变
     */
    private void refreshCategorySpinner() {
        categoryAdapter.clear();
        for (Category c : categoryList) {
            categoryAdapter.add(c.getCategoryName());
        }
        categoryAdapter.notifyDataSetChanged();
    }

    /**
     * 原有逻辑：根据父分类ID刷新子分类Spinner，保持不变
     */
    private void refreshSubCategoryByParentId(int parentCategoryId) {
        subCategoryAdapter.clear();
        for (SubCategory sc : subCategoryList) {
            if (sc.getParentCategoryId() == parentCategoryId) {
                subCategoryAdapter.add(sc.getSubCategoryName());
            }
        }
        subCategoryAdapter.notifyDataSetChanged();

        // 若无子分类，添加兜底选项
        if (subCategoryAdapter.getCount() == 0) {
            subCategoryAdapter.add("暂无子分类");
            subCategoryAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 原有逻辑：刷新位置Spinner，保持不变
     */
    private void refreshLocationSpinner() {
        locationAdapter.clear();
        for (StorageLocation l : storageLocationList) {
            locationAdapter.add(l.getLocationName());
        }
        locationAdapter.notifyDataSetChanged();
    }

    /**
     * 原有逻辑：异步加载要编辑的物品数据并填充到控件，新增：加载物品图片
     */
    private void loadItemDataAsync() {
        new DbAsyncTask<Void, Void, Item>(getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Item>() {
                    @Override
                    public void onDbOperationCompleted(Item resultItem) {
                        // 主线程处理物品数据并填充控件
                        editItem = resultItem;

                        // 校验物品对象有效性
                        if (editItem == null) {
                            Toast.makeText(ItemEditActivity.this, "物品不存在或已被删除！", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        // 填充数据到控件（原有逻辑，保持不变）
                        etEditName.setText(editItem.getItemName() == null ? "" : editItem.getItemName());
                        etEditCount.setText(editItem.getItemCount() == null ? "" : editItem.getItemCount());
                        etEditValidDate.setText(editItem.getValidDate() == null ? "" : editItem.getValidDate());
                        etEditDesc.setText(editItem.getDescription() == null ? "" : editItem.getDescription());

                        // 设置Spinner选中状态（原有逻辑，保持不变）
                        setSpinnerSelection(spEditCategory, editItem.getCategory(), categoryAdapter);
                        setSpinnerSelection(spEditSubCategory, editItem.getSubCategory(), subCategoryAdapter);
                        setSpinnerSelection(spEditLocation, editItem.getLocation(), locationAdapter);

                        // 新增：加载当前物品的已保存图片路径并展示
                        loadItemImagesAsync();
                    }
                }) {
            @Override
            protected Item doInBackground(Void... voids) {
                // 子线程执行数据库查询
                AppDatabase db = AppDatabase.getInstance(mContext);
                return db.itemDao().queryItemById(itemId);
            }
        }.execute();
    }

    /**
     * 新增：异步加载物品图片路径并展示图片
     */
    private void loadItemImagesAsync() {
        new DbAsyncTask<Void, Void, List<String>>(getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<List<String>>() {
                    @Override
                    public void onDbOperationCompleted(List<String> resultPaths) {
                        // 主线程更新图片列表并展示
                        if (resultPaths != null && !resultPaths.isEmpty()) {
                            imagePathList.clear();
                            imagePathList.addAll(resultPaths);
                            // 加载并展示所有已保存的图片
                            loadAndShowSavedImages();
                        }
                    }
                }) {
            @Override
            protected List<String> doInBackground(Void... voids) {
                // 子线程查询当前物品的所有图片路径（需根据你的数据库设计调整，此处为示例）
                AppDatabase db = AppDatabase.getInstance(mContext);
                // 假设你有ItemImageDao，用于查询物品关联的图片路径
                // 请根据你的实际数据库表结构修改此方法
                return db.itemImageDao().queryImagePathsByItemId(itemId);
            }
        }.execute();
    }

    /**
     * 新增：加载并展示所有已保存的图片
     */
    private void loadAndShowSavedImages() {
        // 先清空图片容器，避免重复展示
        llImageContainer.removeAllViews();
        // 遍历图片路径列表，逐个添加预览
        if (imagePathList != null && !imagePathList.isEmpty()) {
            for (String imagePath : imagePathList) {
                addImageToContainer(imagePath);
            }
        }
    }

    /**
     * 原有逻辑：设置Spinner选中对应值，保持不变
     */
    private void setSpinnerSelection(Spinner spinner, String targetValue, ArrayAdapter<String> adapter) {
        if (targetValue == null || adapter == null || spinner == null) {
            return;
        }
        // 遍历适配器，匹配目标值
        for (int i = 0; i < adapter.getCount(); i++) {
            String adapterItem = adapter.getItem(i);
            if (adapterItem != null && adapterItem.equals(targetValue)) {
                spinner.setSelection(i);
                return; // 匹配成功后直接返回
            }
        }
        // 若未匹配到，默认选中第一个选项
        if (adapter.getCount() > 0) {
            spinner.setSelection(0);
        }
    }

    /**
     * 原有逻辑：绑定有效期输入框点击事件，保持不变
     */
    private void bindValidDateClickListener() {
        etEditValidDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    ItemEditActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String validDate = selectedYear + "-" +
                                String.format("%02d", (selectedMonth + 1)) + "-" +
                                String.format("%02d", selectedDay);
                        etEditValidDate.setText(validDate);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });
    }

    /**
     * 新增：绑定添加图片按钮点击事件
     */
    private void bindAddImageClickListener() {
        btnAddImage.setOnClickListener(v -> {
            // 弹出图片选择对话框（相机/图库）
            showImageSelectDialog();
        });
    }

    /**
     * 新增：弹出图片选择对话框
     */
    private void showImageSelectDialog() {
        String[] options = {"相机拍摄", "从图库选择"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择图片来源")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                // 相机拍摄
                                Intent cameraIntent = ImageUtils.getCameraIntent(ItemEditActivity.this);
                                if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                                    startActivityForResult(cameraIntent, REQUEST_CODE_CAMERA);
                                } else {
                                    Toast.makeText(ItemEditActivity.this, "未检测到相机应用", Toast.LENGTH_SHORT).show();
                                }
                                break;
                            case 1:
                                // 图库选择
                                Intent galleryIntent = ImageUtils.getGalleryIntent();
                                startActivityForResult(galleryIntent, REQUEST_CODE_GALLERY);
                                break;
                        }
                    }
                })
                .setCancelable(true)
                .show();
    }

    /**
     * 新增：处理相机/图库返回结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 结果有效
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_CAMERA:
                    // 相机拍摄返回：获取保存的图片路径
                    String cameraPath = ImageUtils.currentPhotoPath;
                    if (cameraPath != null && !cameraPath.isEmpty()) {
                        // 添加到图片列表
                        imagePathList.add(cameraPath);
                        // 预览图片
                        addImageToContainer(cameraPath);
                        // 重置当前照片路径
                        ImageUtils.currentPhotoPath = null;
                    }
                    break;
                case REQUEST_CODE_GALLERY:
                    // 图库选择返回：获取图片Uri并转换为路径
                    if (data != null && data.getData() != null) {
                        Uri imageUri = data.getData();
                        // 简单转换为路径（实际项目可优化为获取真实路径）
                        String galleryPath = getImagePathFromUri(imageUri);
                        if (galleryPath != null && !galleryPath.isEmpty()) {
                            imagePathList.add(galleryPath);
                            addImageToContainer(galleryPath);
                        }
                    }
                    break;
            }
        }
    }

    /**
     * 新增：向容器中添加图片预览
     * @param imagePath 图片路径
     */
    private void addImageToContainer(String imagePath) {
        // 创建ImageView用于预览图片
        ImageView imageView = new ImageView(this);
        // 设置图片参数（自适应大小，避免变形）
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                200, // 宽度
                200, // 高度
                1.0f
        );
        params.setMargins(10, 10, 10, 10);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // 加载图片
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        // 添加到容器
        llImageContainer.addView(imageView);
    }

    /**
     * 新增：从Uri获取图片路径（简化版，适配大部分设备）
     */
    private String getImagePathFromUri(Uri uri) {
        return uri.getPath();
        // 实际项目中可扩展为：通过ContentResolver获取真实文件路径
    }

    /**
     * 原有逻辑：绑定保存修改按钮点击事件，可按需扩展图片保存逻辑
     */
    private void bindSaveEditClickListener() {
        findViewById(R.id.btn_save_edit).setOnClickListener(v -> {
            // 1. 获取修改后的数据（原有逻辑，保持不变）
            String newName = etEditName.getText() != null ? etEditName.getText().toString().trim() : "";
            String newCategory = spEditCategory.getSelectedItem() != null ? spEditCategory.getSelectedItem().toString() : "";
            String newSubCategory = spEditSubCategory.getSelectedItem() != null ? spEditSubCategory.getSelectedItem().toString() : "";
            String newCount = etEditCount.getText() != null ? etEditCount.getText().toString().trim() : "";
            String newLocation = spEditLocation.getSelectedItem() != null ? spEditLocation.getSelectedItem().toString() : "";
            String newValidDate = etEditValidDate.getText() != null ? etEditValidDate.getText().toString().trim() : "";
            String newDesc = etEditDesc.getText() != null ? etEditDesc.getText().toString().trim() : "";

            // 2. 非空校验（物品名称不能为空）
            if (newName.isEmpty()) {
                Toast.makeText(ItemEditActivity.this, "物品名称不能为空！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 3. 校验编辑物品对象有效性
            if (editItem == null) {
                Toast.makeText(ItemEditActivity.this, "待编辑物品不存在！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 4. 更新物品对象（原有逻辑，保持不变）
            editItem.setItemName(newName);
            editItem.setCategory(newCategory);
            editItem.setSubCategory(newSubCategory);
            editItem.setItemCount(newCount);
            editItem.setLocation(newLocation);
            editItem.setValidDate(newValidDate);
            editItem.setDescription(newDesc);

            // 5. 异步更新数据库（原有逻辑，保持不变）
            new DbAsyncTask<Item, Void, Boolean>(ItemEditActivity.this, new DbAsyncTask.OnDbOperationListener<Boolean>() {
                @Override
                public void onDbOperationCompleted(Boolean isSuccess) {
                    // 主线程更新UI（提示结果+关闭页面）
                    if (isSuccess) {
                        // 可选：此处可添加图片路径的保存逻辑（将imagePathList同步到数据库）
                        Toast.makeText(ItemEditActivity.this, "修改保存成功！", Toast.LENGTH_SHORT).show();
                        finish(); // 保存成功后返回上一级页面
                    } else {
                        Toast.makeText(ItemEditActivity.this, "修改保存失败！", Toast.LENGTH_SHORT).show();
                    }
                }
            }) {
                @Override
                protected Boolean doInBackground(Item... items) {
                    // 子线程执行数据库更新操作
                    try {
                        if (items == null || items.length == 0) {
                            return false;
                        }
                        Item itemToUpdate = items[0];
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        db.itemDao().updateItem(itemToUpdate);
                        return true; // 更新成功返回true
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false; // 更新失败返回false
                    }
                }
            }.execute(editItem); // 执行异步更新任务，传入待更新的物品对象
        });
    }
}