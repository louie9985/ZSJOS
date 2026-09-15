-- =============================================
-- V240: 初始化支付主体配置
-- 创建两个通联支付主体：学校（默认）和公司
-- Depends on: V239 (payment subject table)
-- Author: Claude
-- Date: 2026-09-15
-- =============================================

-- 开发基线修正：仅初始化租户 1 的 school/company 主体；在 V239 后执行。
-- 重复执行跳过该租户已有的未删除同编码记录，不覆盖配置，不迁移其他租户数据。
-- 已部署环境不以重放本脚本替代升级；回滚需人工核对新增记录及业务引用。
SET NAMES utf8mb4;

-- 检查并插入学校主体（默认主体）
INSERT INTO zsjos_payment_subject (
    subject_code,
    subject_name,
    cusid,
    appid,
    merchant_private_key,
    platform_public_key,
    rsa_type,
    status,
    is_default,
    deleted,
    tenant_id,
    creator,
    create_time,
    updater,
    update_time
)
SELECT 'school', '合肥中世健职业技能培训学校有限公司', '562361082995TKJ', '00252738',
       'MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDeQ/Vp/KSFxtI08F4qwkaEBYpjyT2xpDitvuSuRfIokcqbK4OONOkCdwZPQp0kQ/mNPY4v7oA74x/E/B94vMGqNvBNbJakFBlJrFRUuC2r231fV6MyxX/VKQ94CPXmt9FQEYYLbcQxOCC0a2PQezpwawM6mmNv89w5EtMZByHb8ZGumMSf/P/++ie714WXXXgAKeFGjWRwZXeWTnIPY4ddkhFzkxG4+NX0rfa15SKJmQYsTy7+gThKdm3z+48FzYEO/HwxHgmvWOtnPMsdP9meYPVJxBS7SGSrqozaTlHlEfpjjtxXOouefGxxAXV2tRjfDjGYeHA/TaqaLImKOz8NAgMBAAECggEAISV2XGN6b57aa12rVWoalQa0gDQbOLo6X6LXvKuetPElC2X+XP9D/oOck8Zl4+olNayH31sMkaHJ85j74ogXw5gk1w8KGhcLPGLwQqAi4328oTLTGje83B+e+HIxpcIk+3FZkavBdeueaPmY7Zbq/Kfg1+T9Tk1KOtK1W4amRIPnW17K0IaTF5wOEXZItL8ixDeho+FNMJpAtdS6FmlwVMHTImK3B287sRfl/9WOzDhOcXBJcfEMlO7RyN977HTlI0v0ruFlHtyWkBcoSzT7nE0WOLAadLZ2QO0ZWUa5ahMiGyTLAyYDPQOtdKBnImW26kT7JerwkxrRq49WYqa5WwKBgQD2voB0Amr924FDdK6vcxoFNursJf75Ghssm8lrBhI2F8iH9NaFx+8DV5UyNcNXuVhImeTQRXDzA9+mdGJfBDnov1kt8MXX8Vp8MSBA4hdrhguFMFZIn/pyK+bYjUezoWY1ZXBQYHUpBIWFDWvFuXdZkUiyyzQ+kcGTZFNi15ODwwKBgQDmmmL8sm+qdv+tey7or4mKj3QArZh2a1nh/mXcEAxy8F3swxeBSkJHbVgTBZktgKK9B09hgao4ko1UtuYBL+4n3xU73jI9fwhqgUfJwNqxdOv7sHAPKpBwlVanjEs2ospl1pwCKaNCHv0+UQfNww9ggsAF6M5qitgLQZvcPmgU7wKBgClT9S35LbSBbKBAzfWDIOuYrDaLkq5kigKpwU3UwX4f862Z+8iCmW0E4W98g9CQsdHPPP0JdIavsmkt+0AQ9CYgzq4cMvcdbNt6Wv2jIOsYk5tmYj1d1lQDOHIzD7xtnzH4YJyF23nYUQjdG50NmIj/BZ802/ZS8YpfdnwKwaGDAoGBAOEAWH8pd40DCRty6uU8iV459DzJJM6+lffM6gQbYJoxb+OYw3FyTFM3HE2LE9dzANahsFF6W+VRETdMMgWMh7o9j5FXjvuz3DlXJP10/61QYQS2NdCGfmJrRB6845JOql9NU/FEQTg91micVZntri5DxSq+6dDI4l2xSWtPNOzfAoGBAKK2/A9KUAumIdxb7B8+A4CHRzF4eTqWXbVgddUTriRVZco3j1J2rJWd8yWazvDagPvXCVoRlVqk0Y1CBQspD1gzy2d7HBkXfrDlQE7/HsVKPw5u7nrv9IZa8cFkqgUp5Yj+eUK4bG7NYOQqSbIY6tHJj7Bn+zAEuu7yLV9Ydq7N', 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCm9OV6zH5DYH/ZnAVYHscEELdCNfNTHGuBv1nYYEY9FrOzE0/4kLl9f7Y9dkWHlc2ocDwbrFSm0Vqz0q2rJPxXUYBCQl5yW3jzuKSXif7q1yOwkFVtJXvuhf5WRy+1X5FOFoMvS7538No0RpnLzmNi3ktmiqmhpcY/1pmt20FHQQIDAQAB', 'RSA2', 0, b'1', b'0', 1, '1', NOW(), '1', NOW()
WHERE NOT EXISTS (SELECT 1 FROM zsjos_payment_subject WHERE tenant_id = 1 AND subject_code = 'school' AND deleted = b'0');

-- 检查并插入公司主体
INSERT INTO zsjos_payment_subject (
    subject_code,
    subject_name,
    cusid,
    appid,
    merchant_private_key,
    platform_public_key,
    rsa_type,
    status,
    is_default,
    deleted,
    tenant_id,
    creator,
    create_time,
    updater,
    update_time
)
SELECT 'company', '安徽省中世健健康管理有限公司', '56036107392AM00', '00333568',
       'MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDsKb/6faBGkmcl7XgK4G2S2VGnxGGnQGbLECpgDFpEf7Tqk4CqM6fUe8Lpowgl8hBP+xUfHC9c2HeP7kU1Lr+Boh3Lo1n8Gi4vPP3/Bm1uKuFfSV73oLn6luVN2P4nRSSDtL0s/RIAV885TeFbQE9SF3AMEqVX5G50M3d2+sIVQ6lOj6ayVyP+5rUGH0yvLNPLWHaF6QdQxUzLe5vTYINFPBrUi5KPKa1Y4wI2bpQNB3F0uk5wvpoVcmVoHef3775rVdzCH3PO0LUGq3k3GEXCe/aLuD/3pVy5q7OT3SWgal3hQolxVUfAZ4Con/ZFDiwX+y26jjmUP7oEYoTGpCUZAgMBAAECggEAKImkGAnPZL3Fsed4xxkuBdqC3zLg9lYiLvIX7APCzOZE9k0adVzHjrwtwXAIOoc6EJiPUBdE9AF+SHkEbTQYkFZ1Gdrw28rcqaQlD00ZpKL8q6ALCqOOuPqE4t+ABNqkAW2ZWWsZV3C7M+Fv8PtxgixewdvDgPpHkW+yLnytqvttTlbZ9zQ+Bejy2hMsvxU3TFQKQCUtJtw5VUpNUIiEGgbTjbAFQyw210JW+L/ZrwrWOGfgagc6AL2jt7xz63YHHNQHjnRIecEj3OXXaP57AcMquz8Ub0ELxUjtccMoo6OYunsIJOmwEcm6wrLtkVFxxcaVVpxJgGVb2Hw9CMrKTwKBgQD/zn7tmzl+2AV1Wfg3S1rRGg9Y17v7izByrn26IANFQLHR4OYdrqAWp5WlISV85Sy7XIsHTY+2GtX8AEuzLBEtEKT9Gy6nxdQPYFRQNCyj/3m+mBrCVLp+Ml46yWA2yJJhxyelVsUFM+yoUfGGB6uO5SBVDNJb0xzTTmM6bKGTtwKBgQDsV3PguDdX73T3jm6WuJP6lCwZekIuGWh46IMSzTsdn28z7KK3LIiWSJhaRUtm2gPZZVDb+prhkUdqum9ZIRI5OKqnAACI5wI7K8HwOeQzq9x9AiZJqgeCARGJhiujK7gSWyvo/H6Z7FLfPzE8s7JCjeJpDaN3+LYHPrd+No8trwKBgQDU2vnJMwGrp4sbNAsTz7M5DYs5rQHx9McSlllt99dg181fS9mPV1BEqZwetK8h9vy6xu203Pg5Wqk2MPTMmV2Ndy0io2y2FCo5xTlDJ7cBm8KtMpoJnEE2Yyj/l7Nkwo7zp6k4rjPw+VzyToU/tsAtfkDiJHIiEFALEE4HPn9QjwKBgFZl6fCep2y4FxRgt0DWHRKflDeEIClmFHEO3svCxvefTzG2Mg2wdAYsZ7WoW1YwIPQaO6MPTAtl5qu3l/kHQ/gzJP+D2q4xdPlYl79QCoJxM1tnq8OGKtR1u4mAyPcPhmz/c1/q3gU7BVija0Z7kpEN+VTaLYYwH6sqXD4Bh/n1AoGBAPZsgly4avMPGOwXNpWFxHLzf2qRTA+DXg7C9Xxkysn5KG1Ydsr94ReQJzZi11/BZf216tSSKm5RPsVGvMg9fwRy0qMxEZvr4iQRVhndadaUKge707iXQ7xkXMvJ7hR1K6BqfFMLs1rOnG2EYlvQ/qUF4qMdBnqYuBleywNa/YHU
', 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCm9OV6zH5DYH/ZnAVYHscEELdCNfNTHGuBv1nYYEY9FrOzE0/4kLl9f7Y9dkWHlc2ocDwbrFSm0Vqz0q2rJPxXUYBCQl5yW3jzuKSXif7q1yOwkFVtJXvuhf5WRy+1X5FOFoMvS7538No0RpnLzmNi3ktmiqmhpcY/1pmt20FHQQIDAQAB', 'RSA2', 0, b'0', b'0', 1, '1', NOW(), '1', NOW()
WHERE NOT EXISTS (SELECT 1 FROM zsjos_payment_subject WHERE tenant_id = 1 AND subject_code = 'company' AND deleted = b'0');

-- 更新版本记录
INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
VALUES ('V240', '初始化支付主体配置', SHA2('V240__init_payment_subjects.sql', 256), NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);

INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
VALUES ('payment', 'V240', '初始化两个通联支付主体', SHA2('V240__init_payment_subjects.sql', 256), 'v1.0', NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum), release_version=VALUES(release_version);
