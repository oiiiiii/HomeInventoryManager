package com.example.homeinventorymanager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;

import com.example.homeinventorymanager.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // 视图绑定对象（自动生成，对应 activity_main.xml）
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化视图绑定，替代 setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 默认显示 物品录入 Fragment
        replaceFragment(new ItemAddFragment());

        // 底部导航栏点击事件监听
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_add) {
                replaceFragment(new ItemAddFragment());
                return true;
            } else if (itemId == R.id.nav_query) {
                replaceFragment(new ItemQueryFragment());
                return true;
            } else if (itemId == R.id.nav_modify) {
                replaceFragment(new ItemModifyFragment());
                return true;
            } else if (itemId == R.id.nav_setting) {
                replaceFragment(new SettingFragment());
                return true;
            }
            return false;
        });
    }

    /**
     * 替换 Fragment 工具方法（复用代码，避免冗余）
     * @param fragment 要显示的 Fragment
     */
    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment); // 替换容器中的 Fragment
        transaction.commit(); // 提交事务
    }
}