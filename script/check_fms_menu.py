#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 FMS 财管系统的完整菜单结构"""

import pymysql
from pymysql.cursors import DictCursor

DB_CONFIG = {
    'host': '192.168.2.17',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'ruoyi-vue-pro',
    'charset': 'utf8mb4'
}

def check_fms_menu():
    try:
        conn = pymysql.connect(**DB_CONFIG, cursorclass=DictCursor)
        cursor = conn.cursor()

        print("=" * 80)
        print("【FMS 财管系统】的完整菜单结构")
        print("=" * 80)

        # 查询 FMS 及其子菜单
        cursor.execute("""
            SELECT id, name, parent_id, type, path, sort, creator, create_time
            FROM system_menu
            WHERE (id = 601894 OR parent_id = 601894) AND deleted = 0
            ORDER BY parent_id, sort
        """)
        results = cursor.fetchall()

        for row in results:
            menu_type = {1: '目录', 2: '菜单', 3: '按钮'}.get(row['type'], '未知')
            indent = '  ' if row['parent_id'] == 601894 else ''
            print(f"{indent}ID: {row['id']}, 名称: {row['name']}, 类型: {menu_type}, "
                  f"路径: {row['path']}, 排序: {row['sort']}, "
                  f"创建者: {row['creator']}, 创建时间: {row['create_time']}")

        print("\n" + "=" * 80)
        print("【设置管理】(601951) 的原始信息")
        print("=" * 80)
        cursor.execute("""
            SELECT id, name, parent_id, path, component, sort, creator,
                   create_time, updater, update_time
            FROM system_menu
            WHERE id = 601951
        """)
        result = cursor.fetchone()
        if result:
            print(f"ID: {result['id']}")
            print(f"名称: {result['name']}")
            print(f"父ID: {result['parent_id']}")
            print(f"路径: {result['path']}")
            print(f"组件: {result['component']}")
            print(f"排序: {result['sort']}")
            print(f"创建者: {result['creator']}")
            print(f"创建时间: {result['create_time']}")
            print(f"更新者: {result['updater']}")
            print(f"更新时间: {result['update_time']}")
        else:
            print("❌ 未找到设置管理菜单")

        cursor.close()
        conn.close()

    except Exception as e:
        print(f"❌ 错误: {e}")


if __name__ == '__main__':
    check_fms_menu()
