#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查菜单权限绑定"""

import pymysql
from pymysql.cursors import DictCursor
import sys
import io

# 设置 stdout 为 UTF-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

DB_CONFIG = {
    'host': '192.168.2.17',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'ruoyi-vue-pro',
    'charset': 'utf8mb4'
}

def check_permission():
    try:
        conn = pymysql.connect(**DB_CONFIG, cursorclass=DictCursor)
        cursor = conn.cursor()

        print("=" * 80)
        print("【1】检查支付主体菜单是否存在")
        print("=" * 80)
        cursor.execute("""
            SELECT id, name, parent_id, path, visible, status, deleted
            FROM system_menu
            WHERE id IN (6850, 6851)
        """)
        results = cursor.fetchall()
        if results:
            for row in results:
                visible_text = "显示" if row['visible'] == 1 else "隐藏"
                status_text = "正常" if row['status'] == 0 else "禁用"
                deleted_text = "已删除" if row['deleted'] == 1 else "正常"
                print(f"ID: {row['id']}, 名称: {row['name']}, 父ID: {row['parent_id']}, "
                      f"路径: {row['path']}, 可见: {visible_text}, "
                      f"状态: {status_text}, 删除: {deleted_text}")
        else:
            print("❌ 未找到支付主体菜单 (6850, 6851)")
            return

        print("\n" + "=" * 80)
        print("【2】检查管理员角色")
        print("=" * 80)
        cursor.execute("""
            SELECT id, name, code, type, status
            FROM system_role
            WHERE code = 'super_admin' OR name = '超级管理员'
        """)
        result = cursor.fetchone()
        if result:
            admin_role_id = result['id']
            print(f"管理员角色 ID: {admin_role_id}, 名称: {result['name']}, "
                  f"编码: {result['code']}, 状态: {result['status']}")
        else:
            print("❌ 未找到管理员角色")
            return

        print("\n" + "=" * 80)
        print("【3】检查管理员是否绑定了支付主体菜单")
        print("=" * 80)
        cursor.execute("""
            SELECT menu_id
            FROM system_role_menu
            WHERE role_id = %s AND menu_id IN (6850, 6851)
        """, (admin_role_id,))
        results = cursor.fetchall()
        if results:
            for row in results:
                print(f"✅ 管理员已绑定菜单 ID: {row['menu_id']}")
        else:
            print(f"❌ 管理员（角色 ID: {admin_role_id}）未绑定支付主体菜单！")
            print(f"\n需要执行绑定 SQL：")
            print("请通过系统角色管理配置所需菜单权限。")

        print("\n" + "=" * 80)
        print("【4】检查财务主管角色")
        print("=" * 80)
        cursor.execute("""
            SELECT id, name, code, status
            FROM system_role
            WHERE name LIKE '%财务%' OR code LIKE '%finance%'
        """)
        results = cursor.fetchall()
        if results:
            for row in results:
                finance_role_id = row['id']
                print(f"\n角色 ID: {finance_role_id}, 名称: {row['name']}, 编码: {row['code']}")

                # 检查是否绑定
                cursor.execute("""
                    SELECT menu_id
                    FROM system_role_menu
                    WHERE role_id = %s AND menu_id IN (6850, 6851)
                """, (finance_role_id,))
                bindings = cursor.fetchall()
                if bindings:
                    for b in bindings:
                        print(f"  ✅ 已绑定菜单 ID: {b['menu_id']}")
                else:
                    print(f"  ❌ 未绑定支付主体菜单")
        else:
            print("❌ 未找到财务相关角色")

        print("\n" + "=" * 80)
        print("【5】检查你当前登录的用户")
        print("=" * 80)
        print("请告诉我你的用户名，我帮你检查角色绑定")

        cursor.close()
        conn.close()

    except Exception as e:
        print(f"❌ 错误: {e}")


if __name__ == '__main__':
    check_permission()
