package com.example.homeinventorymanager;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.homeinventorymanager.databinding.FragmentSettingBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置界面：自定义分类（带联动）、子分类、存放位置
 * 已修复：异步数据库操作、空指针、主线程阻塞、列表越界等问题
 */
public class SettingFragment extends Fragment {

    private FragmentSettingBinding binding;

    // 数据列表
    private List<Category> categoryList;
    private List<SubCategory> subCategoryList;
    private List<StorageLocation> storageLocationList;

    // 适配器
    private CategoryAdapter categoryAdapter;
    private SubCategoryAdapter subCategoryAdapter;
    private StorageLocationAdapter storageLocationAdapter;
    private ArrayAdapter<String> parentCategorySpinnerAdapter;

    // 兜底默认数据（无自定义数据时使用）
    private String[] defaultCategories = {"食品", "日用品", "家电", "服饰", "其他"};
    private String[] defaultSubCategories = {"零食", "生鲜", "调味品", "清洁用品", "洗漱用品", "其他"};
    private String[] defaultLocations = {"冰箱", "厨房橱柜", "卫生间", "卧室衣柜", "客厅书架", "阳台", "其他"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSettingBinding.inflate(inflater, container, false);

        // 1. 初始化数据列表
        initDataLists();

        // 2. 初始化适配器
        initAdapters();

        // 3. 绑定添加按钮点击事件
        bindAddButtonClickListeners();

        // 4. 异步加载数据库数据（核心修复：避免主线程阻塞）
        loadAllDataFromDbAsync();

        // 5. 刷新父分类Spinner（用于子分类关联）
        refreshParentCategorySpinner();

        return binding.getRoot();
    }

    /**
     * 初始化数据列表
     */
    private void initDataLists() {
        categoryList = new ArrayList<>();
        subCategoryList = new ArrayList<>();
        storageLocationList = new ArrayList<>();
    }

    /**
     * 初始化适配器
     */
    private void initAdapters() {
        // 分类适配器
        categoryAdapter = new CategoryAdapter();
        binding.lvCategoryList.setAdapter(categoryAdapter);

        // 子分类适配器
        subCategoryAdapter = new SubCategoryAdapter();
        binding.lvSubCategoryList.setAdapter(subCategoryAdapter);

        // 存放位置适配器
        storageLocationAdapter = new StorageLocationAdapter();
        binding.lvLocationList.setAdapter(storageLocationAdapter);

        // 父分类Spinner适配器（用于选择子分类所属父分类）
        parentCategorySpinnerAdapter = new ArrayAdapter<>(
                requireActivity(), // 安全获取上下文，替代getActivity()
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        parentCategorySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spParentCategory.setAdapter(parentCategorySpinnerAdapter);
    }

    /**
     * 绑定所有添加按钮的点击事件（异步操作，避免主线程阻塞）
     */
    private void bindAddButtonClickListeners() {
        // 1. 添加分类（异步插入数据库）
        binding.btnAddCategory.setOnClickListener(v -> {
            // 空安全获取输入内容
            String categoryName = binding.etAddCategory.getText() != null ? binding.etAddCategory.getText().toString().trim() : "";
            if (categoryName.isEmpty()) {
                Toast.makeText(requireActivity(), "请输入分类名称！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 避免重复添加（内存列表中校验）
            boolean isExist = false;
            for (Category c : categoryList) {
                if (c.getCategoryName() != null && c.getCategoryName().equals(categoryName)) {
                    isExist = true;
                    break;
                }
            }
            if (isExist) {
                Toast.makeText(requireActivity(), "该分类已存在！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建分类对象
            Category newCategory = new Category(categoryName);

            // 异步插入数据库
            new DbAsyncTask<Category, Void, Boolean>(requireActivity().getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<Boolean>() {
                        @Override
                        public void onDbOperationCompleted(Boolean isSuccess) {
                            if (isSuccess) {
                                // 主线程更新UI和数据
                                binding.etAddCategory.setText("");
                                loadAllDataFromDbAsync(); // 重新加载最新数据
                                refreshParentCategorySpinner();
                                Toast.makeText(requireActivity(), "分类添加成功！", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireActivity(), "分类添加失败！", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }) {
                @Override
                protected Boolean doInBackground(Category... categories) {
                    try {
                        if (categories == null || categories.length == 0) {
                            return false;
                        }
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        db.categoryDao().insertCategory(categories[0]);
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }.execute(newCategory);
        });

        // 2. 添加子分类（核心：关联父分类，异步插入）
        binding.btnAddSubCategory.setOnClickListener(v -> {
            // 空安全获取父分类
            String parentCategoryName = binding.spParentCategory.getSelectedItem() != null ? binding.spParentCategory.getSelectedItem().toString() : "";
            if (parentCategoryName.isEmpty()) {
                Toast.makeText(requireActivity(), "请先选择父分类！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 空安全获取子分类名称
            String subCategoryName = binding.etAddSubCategory.getText() != null ? binding.etAddSubCategory.getText().toString().trim() : "";
            if (subCategoryName.isEmpty()) {
                Toast.makeText(requireActivity(), "请输入子分类名称！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取父分类ID
            Category parentCategory = null;
            for (Category c : categoryList) {
                if (c.getCategoryName() != null && c.getCategoryName().equals(parentCategoryName)) {
                    parentCategory = c;
                    break;
                }
            }
            if (parentCategory == null) {
                Toast.makeText(requireActivity(), "父分类不存在！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 避免同一父分类下重复添加子分类
            boolean isSubExist = false;
            for (SubCategory sc : subCategoryList) {
                if (sc.getSubCategoryName() != null && sc.getSubCategoryName().equals(subCategoryName)
                        && sc.getParentCategoryId() == parentCategory.getId()) {
                    isSubExist = true;
                    break;
                }
            }
            if (isSubExist) {
                Toast.makeText(requireActivity(), "该父分类下已存在该子分类！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建子分类对象
            SubCategory newSubCategory = new SubCategory(subCategoryName, parentCategory.getId(), parentCategoryName);

            // 异步插入数据库
            new DbAsyncTask<SubCategory, Void, Boolean>(requireActivity().getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<Boolean>() {
                        @Override
                        public void onDbOperationCompleted(Boolean isSuccess) {
                            if (isSuccess) {
                                binding.etAddSubCategory.setText("");
                                loadAllDataFromDbAsync();
                                Toast.makeText(requireActivity(), "子分类添加成功！", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireActivity(), "子分类添加失败！", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }) {
                @Override
                protected Boolean doInBackground(SubCategory... subCategories) {
                    try {
                        if (subCategories == null || subCategories.length == 0) {
                            return false;
                        }
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        db.subCategoryDao().insertSubCategory(subCategories[0]);
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }.execute(newSubCategory);
        });

        // 3. 添加存放位置（异步插入数据库）
        binding.btnAddLocation.setOnClickListener(v -> {
            // 空安全获取位置名称
            String locationName = binding.etAddLocation.getText() != null ? binding.etAddLocation.getText().toString().trim() : "";
            if (locationName.isEmpty()) {
                Toast.makeText(requireActivity(), "请输入位置名称！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 避免重复添加
            boolean isLocExist = false;
            for (StorageLocation l : storageLocationList) {
                if (l.getLocationName() != null && l.getLocationName().equals(locationName)) {
                    isLocExist = true;
                    break;
                }
            }
            if (isLocExist) {
                Toast.makeText(requireActivity(), "该位置已存在！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建位置对象
            StorageLocation newLocation = new StorageLocation(locationName);

            // 异步插入数据库
            new DbAsyncTask<StorageLocation, Void, Boolean>(requireActivity().getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<Boolean>() {
                        @Override
                        public void onDbOperationCompleted(Boolean isSuccess) {
                            if (isSuccess) {
                                binding.etAddLocation.setText("");
                                loadAllDataFromDbAsync();
                                Toast.makeText(requireActivity(), "位置添加成功！", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireActivity(), "位置添加失败！", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }) {
                @Override
                protected Boolean doInBackground(StorageLocation... storageLocations) {
                    try {
                        if (storageLocations == null || storageLocations.length == 0) {
                            return false;
                        }
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        db.storageLocationDao().insertStorageLocation(storageLocations[0]);
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }.execute(newLocation);
        });
    }

    /**
     * 异步从数据库加载所有数据（核心修复：避免主线程阻塞）
     */
    private void loadAllDataFromDbAsync() {
        new DbAsyncTask<Void, Void, Void>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Void>() {
                    @Override
                    public void onDbOperationCompleted(Void aVoid) {
                        // 主线程刷新所有适配器（UI操作必须在主线程）
                        categoryAdapter.notifyDataSetChanged();
                        subCategoryAdapter.notifyDataSetChanged();
                        storageLocationAdapter.notifyDataSetChanged();
                        // 同步刷新父分类Spinner
                        refreshParentCategorySpinner();
                    }
                }) {
            @Override
            protected Void doInBackground(Void... voids) {
                // 子线程执行数据库查询
                AppDatabase db = AppDatabase.getInstance(mContext);

                // 1. 加载分类
                categoryList.clear();
                List<Category> dbCategories = db.categoryDao().queryAllCategories();
                if (dbCategories != null && !dbCategories.isEmpty()) {
                    categoryList.addAll(dbCategories);
                } else {
                    // 无自定义分类时，添加默认分类
                    for (String defaultName : defaultCategories) {
                        categoryList.add(new Category(defaultName));
                    }
                }

                // 2. 加载子分类
                subCategoryList.clear();
                List<SubCategory> dbSubCategories = db.subCategoryDao().queryAllSubCategories();
                if (dbSubCategories != null && !dbSubCategories.isEmpty()) {
                    subCategoryList.addAll(dbSubCategories);
                } else {
                    // 无自定义子分类时，添加默认子分类（关联默认第一个分类）
                    if (!categoryList.isEmpty()) {
                        Category defaultParent = categoryList.get(0);
                        for (String defaultName : defaultSubCategories) {
                            subCategoryList.add(new SubCategory(defaultName, defaultParent.getId(), defaultParent.getCategoryName()));
                        }
                    }
                }

                // 3. 加载存放位置
                storageLocationList.clear();
                List<StorageLocation> dbLocations = db.storageLocationDao().queryAllStorageLocations();
                if (dbLocations != null && !dbLocations.isEmpty()) {
                    storageLocationList.addAll(dbLocations);
                } else {
                    // 无自定义位置时，添加默认位置
                    for (String defaultName : defaultLocations) {
                        storageLocationList.add(new StorageLocation(defaultName));
                    }
                }
                return null;
            }
        }.execute();
    }

    /**
     * 刷新父分类Spinner（用于子分类关联选择）
     */
    private void refreshParentCategorySpinner() {
        parentCategorySpinnerAdapter.clear();
        for (Category c : categoryList) {
            if (c.getCategoryName() != null) {
                parentCategorySpinnerAdapter.add(c.getCategoryName());
            }
        }
        parentCategorySpinnerAdapter.notifyDataSetChanged();
    }

    /**
     * 分类列表适配器（支持长按异步删除，增加空安全判断）
     */
    private class CategoryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return categoryList.size();
        }

        @Override
        public Object getItem(int position) {
            return (position >= 0 && position < categoryList.size()) ? categoryList.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            // 视图复用，提升性能
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.tvContent = convertView.findViewById(android.R.id.text1);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // 空安全判断，避免列表越界
            if (position < 0 || position >= categoryList.size()) {
                return convertView;
            }
            Category category = categoryList.get(position);
            if (category == null || category.getCategoryName() == null) {
                return convertView;
            }
            viewHolder.tvContent.setText(category.getCategoryName());

            // 长按删除（异步操作，避免主线程阻塞）
            convertView.setOnLongClickListener(v -> {
                Category delCategory = categoryList.get(position);
                if (delCategory == null) {
                    Toast.makeText(requireActivity(), "获取分类数据失败！", Toast.LENGTH_SHORT).show();
                    return true;
                }

                new AlertDialog.Builder(requireActivity())
                        .setTitle("删除确认")
                        .setMessage("确定要删除分类【" + delCategory.getCategoryName() + "】吗？该分类下的子分类也会被删除！")
                        .setPositiveButton("确定", (dialog, which) -> {
                            // 异步删除分类
                            new DbAsyncTask<Category, Void, Boolean>(requireActivity().getApplicationContext(),
                                    new DbAsyncTask.OnDbOperationListener<Boolean>() {
                                        @Override
                                        public void onDbOperationCompleted(Boolean isSuccess) {
                                            if (isSuccess) {
                                                loadAllDataFromDbAsync(); // 重新加载数据
                                                refreshParentCategorySpinner();
                                                Toast.makeText(requireActivity(), "分类删除成功！", Toast.LENGTH_SHORT).show();
                                            } else {
                                                Toast.makeText(requireActivity(), "分类删除失败！", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    }) {
                                @Override
                                protected Boolean doInBackground(Category... categories) {
                                    try {
                                        if (categories == null || categories.length == 0) {
                                            return false;
                                        }
                                        AppDatabase db = AppDatabase.getInstance(mContext);
                                        // 删除分类
                                        db.categoryDao().deleteCategory(categories[0]);
                                        return true;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return false;
                                    }
                                }
                            }.execute(delCategory);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });

            return convertView;
        }

        class ViewHolder {
            TextView tvContent;
        }
    }

    /**
     * 子分类列表适配器（支持长按异步删除，显示隶属关系）
     */
    private class SubCategoryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return subCategoryList.size();
        }

        @Override
        public Object getItem(int position) {
            return (position >= 0 && position < subCategoryList.size()) ? subCategoryList.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.tvTitle = convertView.findViewById(android.R.id.text1);
                viewHolder.tvSubtitle = convertView.findViewById(android.R.id.text2);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // 空安全判断
            if (position < 0 || position >= subCategoryList.size()) {
                return convertView;
            }
            SubCategory subCategory = subCategoryList.get(position);
            if (subCategory == null) {
                return convertView;
            }

            // 空值兜底
            String subCatName = subCategory.getSubCategoryName() == null ? "" : subCategory.getSubCategoryName();
            String parentCatName = subCategory.getParentCategoryName() == null ? "" : subCategory.getParentCategoryName();

            viewHolder.tvTitle.setText(subCatName);
            viewHolder.tvSubtitle.setText("隶属：" + parentCatName);

            // 长按删除（异步操作）
            convertView.setOnLongClickListener(v -> {
                SubCategory delSubCategory = subCategoryList.get(position);
                if (delSubCategory == null) {
                    Toast.makeText(requireActivity(), "获取子分类数据失败！", Toast.LENGTH_SHORT).show();
                    return true;
                }

                new AlertDialog.Builder(requireActivity())
                        .setTitle("删除确认")
                        .setMessage("确定要删除子分类【" + delSubCategory.getSubCategoryName() + "】吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            // 异步删除子分类
                            new DbAsyncTask<SubCategory, Void, Boolean>(requireActivity().getApplicationContext(),
                                    new DbAsyncTask.OnDbOperationListener<Boolean>() {
                                        @Override
                                        public void onDbOperationCompleted(Boolean isSuccess) {
                                            if (isSuccess) {
                                                loadAllDataFromDbAsync();
                                                Toast.makeText(requireActivity(), "子分类删除成功！", Toast.LENGTH_SHORT).show();
                                            } else {
                                                Toast.makeText(requireActivity(), "子分类删除失败！", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    }) {
                                @Override
                                protected Boolean doInBackground(SubCategory... subCategories) {
                                    try {
                                        if (subCategories == null || subCategories.length == 0) {
                                            return false;
                                        }
                                        AppDatabase db = AppDatabase.getInstance(mContext);
                                        db.subCategoryDao().deleteSubCategory(subCategories[0]);
                                        return true;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return false;
                                    }
                                }
                            }.execute(delSubCategory);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });

            return convertView;
        }

        class ViewHolder {
            TextView tvTitle;
            TextView tvSubtitle;
        }
    }

    /**
     * 存放位置列表适配器（支持长按异步删除）
     */
    private class StorageLocationAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return storageLocationList.size();
        }

        @Override
        public Object getItem(int position) {
            return (position >= 0 && position < storageLocationList.size()) ? storageLocationList.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.tvContent = convertView.findViewById(android.R.id.text1);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // 空安全判断
            if (position < 0 || position >= storageLocationList.size()) {
                return convertView;
            }
            StorageLocation location = storageLocationList.get(position);
            if (location == null || location.getLocationName() == null) {
                return convertView;
            }
            viewHolder.tvContent.setText(location.getLocationName());

            // 长按删除（异步操作）
            convertView.setOnLongClickListener(v -> {
                StorageLocation delLocation = storageLocationList.get(position);
                if (delLocation == null) {
                    Toast.makeText(requireActivity(), "获取位置数据失败！", Toast.LENGTH_SHORT).show();
                    return true;
                }

                new AlertDialog.Builder(requireActivity())
                        .setTitle("删除确认")
                        .setMessage("确定要删除位置【" + delLocation.getLocationName() + "】吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            // 异步删除位置
                            new DbAsyncTask<StorageLocation, Void, Boolean>(requireActivity().getApplicationContext(),
                                    new DbAsyncTask.OnDbOperationListener<Boolean>() {
                                        @Override
                                        public void onDbOperationCompleted(Boolean isSuccess) {
                                            if (isSuccess) {
                                                loadAllDataFromDbAsync();
                                                Toast.makeText(requireActivity(), "位置删除成功！", Toast.LENGTH_SHORT).show();
                                            } else {
                                                Toast.makeText(requireActivity(), "位置删除失败！", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    }) {
                                @Override
                                protected Boolean doInBackground(StorageLocation... storageLocations) {
                                    try {
                                        if (storageLocations == null || storageLocations.length == 0) {
                                            return false;
                                        }
                                        AppDatabase db = AppDatabase.getInstance(mContext);
                                        db.storageLocationDao().deleteStorageLocation(storageLocations[0]);
                                        return true;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return false;
                                    }
                                }
                            }.execute(delLocation);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });

            return convertView;
        }

        class ViewHolder {
            TextView tvContent;
        }
    }

    /**
     * 销毁视图时释放绑定对象，避免内存泄漏
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}