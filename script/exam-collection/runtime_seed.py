# -*- coding: utf-8 -*-
"""Synthetic data only, strictly scoped to the retained isolated runtime database."""
from pathlib import Path
import json, re, secrets, yaml, pymysql, datetime, subprocess, os, argparse
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/exam-collection/runtime'
d = yaml.safe_load((OUT / 'application-exam-isolated.yaml').read_text(encoding='utf-8'))
c = d['spring']['datasource']['dynamic']['datasource']['master']
a = re.search('jdbc:mysql://([^:/]+):(\\d+)/([^?]+)', c['url'])
parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
args = parser.parse_args()
manifest = json.loads((OUT / 'manifest.json').read_text(encoding='utf-8'))
if not (a[3].startswith('exam_collection_runtime_') and a[3] == manifest['database'] == args.database):
    raise RuntimeError('Runtime seed target mismatch')
connection = pymysql.connect(
    host=a[1],
    port=int(a[2]),
    user=c['username'],
    password=c['password'],
    database=a[3],
    charset='utf8mb4',
    autocommit=False
)
credential_path = OUT / 'synthetic-credentials.json'
credentials = json.loads(credential_path.read_text()) if credential_path.exists() else {k: secrets.token_urlsafe(32) for k in ['setup', 'exam', 'planner', 'outsider']}
credentials.setdefault('planner2', secrets.token_urlsafe(32))
auth_sessions = {}
java_args = (ROOT / 'artifacts/exam-collection/backend/java.args').read_text(encoding='utf-8').splitlines()
classpath = java_args[java_args.index('"-cp"') + 1].strip('"')
generator = OUT / 'RuntimeFields.java'
generator.write_text(
    'import java.nio.file.*;\nimport cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;\nimport cn.iocoder.yudao.framework.common.util.json.JsonUtils;\nclass RuntimeFields {public static void main(String[] args) throws Exception {\nFiles.writeString(Path.of(args[0]),JsonUtils.toJsonString(new StudentInfoFields().presets()));}}',
    encoding='utf-8'
)

def java_run(command, args):
    path = OUT / (command + '-fields.args')
    path.write_text('\n'.join(('"' + str(v).replace('\\', '/') + '"' for v in args)), encoding='utf-8')
    subprocess.run([command, '@' + str(path)], check=True, capture_output=True)
java_run('javac', ['-encoding', 'UTF-8', '-cp', classpath, '-d', str(OUT), str(generator)])
java_run(
    'java',
    ['-cp', str(OUT) + os.pathsep + classpath, 'RuntimeFields', str(OUT / 'fields.json')]
)

def insert(cursor, table, values):
    if table in ['system_user_role', 'system_role_menu']:
        where = ' AND '.join(('`' + key + '`=%s' for key in values))
        cursor.execute(f'SELECT COUNT(*) FROM `{table}` WHERE {where}', tuple(values.values()))
        if cursor.fetchone()[0]:
            return
    columns = ','.join(('`' + key + '`' for key in values))
    cursor.execute(
        f"INSERT IGNORE INTO `{table}` ({columns}) VALUES ({','.join(['%s'] * len(values))})",
        tuple(values.values())
    )
with connection.cursor() as cursor:
    cursor.execute('SET NAMES utf8mb4')
    insert(
        cursor,
        'system_tenant',
        dict(
            id=1,
            name='考试收集隔离验收',
            contact_name='合成管理员',
            status=0,
            package_id=0,
            expire_time='2099-01-01',
            account_count=100
        )
    )
    insert(
        cursor,
        'system_oauth2_client',
        dict(
            id=910001,
            client_id='default',
            secret='synthetic-fixture',
            name='Synthetic fixture',
            logo='',
            status=0,
            access_token_validity_seconds=86400,
            refresh_token_validity_seconds=172800,
            redirect_uris='["http://127.0.0.1:5189"]',
            authorized_grant_types='["password","refresh_token"]'
        )
    )
    for index, key in enumerate(credentials):
        uid = 910001 + index
        rid = 910001 + index
        insert(
            cursor,
            'system_users',
            dict(
                id=uid,
                username='exam_fixture_' + key,
                nickname='合成' + key,
                password='disabled-login',
                status=0,
                tenant_id=1
            )
        )
        insert(
            cursor,
            'system_role',
            dict(
                id=rid,
                name='合成' + key,
                code='super_admin' if key == 'setup' else 'exam_fixture_' + key,
                sort=index,
                status=0,
                type=2,
                tenant_id=1
            )
        )
        insert(cursor, 'system_user_role', dict(user_id=uid, role_id=rid, tenant_id=1))
        if key == 'exam':
            cursor.execute("SELECT id FROM system_menu WHERE permission LIKE 'zsjos:exam-collection:%%' AND deleted=0")
        elif key in ['planner', 'planner2']:
            cursor.execute("SELECT id FROM system_menu WHERE permission='zsjos:business-task:query' AND deleted=0")
        else:
            cursor.execute('SELECT id FROM system_menu WHERE 1=0')
        pending = [row[0] for row in cursor.fetchall()]
        visited = set()
        while pending:
            menu = pending.pop()
            if menu in visited or not menu:
                continue
            visited.add(menu)
            insert(cursor, 'system_role_menu', dict(role_id=rid, menu_id=menu, tenant_id=1))
            cursor.execute('SELECT parent_id FROM system_menu WHERE id=%s', (menu,))
            parent = cursor.fetchone()
            if parent:
                pending.append(parent[0])
        cursor.execute(
            'SELECT COUNT(*) FROM system_oauth2_access_token WHERE access_token=%s AND tenant_id=1',
            (credentials[key],)
        )
        token_count = cursor.fetchone()[0]
        if token_count > 1:
            credentials[key] = secrets.token_urlsafe(32)
        if token_count != 1:
            insert(
                cursor,
                'system_oauth2_access_token',
                dict(
                    id=uid,
                    access_token=credentials[key],
                    refresh_token=secrets.token_urlsafe(32),
                    user_id=uid,
                    user_type=2,
                    user_info=json.dumps({'nickname': 'synthetic'}),
                    client_id='default',
                    scopes='[]',
                    expires_time='2099-01-01',
                    tenant_id=1
                )
            )
        cursor.execute(
            'SELECT refresh_token FROM system_oauth2_access_token WHERE access_token=%s AND tenant_id=1',
            (credentials[key],)
        )
        refresh = cursor.fetchone()[0]
        insert(
            cursor,
            'system_oauth2_refresh_token',
            dict(
                id=uid,
                refresh_token=refresh,
                user_id=uid,
                user_type=2,
                client_id='default',
                scopes='[]',
                expires_time='2099-01-01',
                tenant_id=1
            )
        )
        auth_sessions[key] = {'accessToken': credentials[key], 'refreshToken': refresh}
    local = OUT / 'private-storage'
    local.mkdir(exist_ok=True)
    insert(
        cursor,
        'infra_file_config',
        dict(
            id=910001,
            name='隔离本地存储',
            storage=10,
            master=1,
            config=json.dumps({
                '@class': 'cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig',
                'basePath': str(local),
                'domain': 'http://127.0.0.1:48089'
            })
        )
    )
    insert(
        cursor,
        'zsjos_student_info_form_config',
        dict(
            id=910001,
            version_no=1,
            revision=0,
            status='PUBLISHED',
            fields_json=(OUT / 'fields.json').read_text(encoding='utf-8'),
            education_category_ids_json='[]',
            tenant_id=1,
            published_at=datetime.datetime.now()
        )
    )
    for i in range(2):
        insert(
            cursor,
            'zsjos_delivery_class',
            dict(
                id=920001 + i,
                class_no='EXAM-FIXTURE-' + str(i),
                class_name='合成验收班' + str(i),
                system_class=0,
                class_kind='NORMAL',
                homeroom_user_id=910003 if i == 0 else 910005,
                homeroom_user_name_snapshot='合成规划师' + str(i + 1),
                status='SERVING',
                tenant_id=1
            )
        )
    cursor.execute(
        'UPDATE zsjos_delivery_class SET homeroom_user_id=910005 WHERE id=920002 AND tenant_id=1 AND class_no=%s',
        ('EXAM-FIXTURE-1',)
    )
    for i in range(3):
        insert(
            cursor,
            'zsjos_person',
            dict(
                id=930001 + i,
                person_no='EXAM-PERSON-' + str(i),
                name='合成学员' + str(i),
                mobile='1380000000' + str(i),
                first_seen_at='2026-11-04',
                last_seen_at='2026-11-04',
                tenant_id=1
            )
        )
        insert(
            cursor,
            'zsjos_service_relation',
            dict(
                id=940001 + i,
                person_id=930001 + i,
                student_name_snapshot='合成学员' + str(i),
                teaching_delivery=1,
                teaching_closed=0,
                class_id=920001 if i < 2 else 920002,
                status='active',
                activated_at='2026-11-04',
                tenant_id=1
            )
        )
    cursor.execute("UPDATE zsjos_service_relation SET status='active' WHERE tenant_id=1 AND id BETWEEN 940001 AND 940003 AND status='SERVING'")
connection.commit()
connection.close()
credential_path.write_text(json.dumps(credentials), encoding='utf-8')
(OUT / 'synthetic-auth-sessions.json').write_text(json.dumps(auth_sessions), encoding='utf-8')
d['zsjos']['student-info']['public-base-url'] = 'http://localhost:5190'
(OUT / 'application-exam-isolated.yaml').write_text(yaml.safe_dump(d, allow_unicode=True, sort_keys=False), encoding='utf-8')
print('Synthetic accounts, scoped grants, classes, services and private local storage seeded; credentials retained only in local artifact.')
