# -*- coding: utf-8 -*-
"""Execute the actual mapper SQL on retained synthetic MySQL tables; no application writes."""
import re
import uuid
from test_positioning_single_source import query, ROOT

def main():
    database = 'rv_' + uuid.uuid4().hex[:10]
    query('ruoyi-vue-pro', f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    query(database, "CREATE TABLE zsjos_media_account(id bigint PRIMARY KEY,tenant_id bigint); INSERT INTO zsjos_media_account VALUES(123,1);")
    for package, table in [('contentreview/ContentReviewBatchMapper.java','zsjos_content_review_batch'),('production/ProductionTicketMapper.java','zsjos_production_ticket')]:
        path = ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql' / package
        sql = re.search(r'@org.apache.ibatis.annotations.Update\("([^\n]+)"\)', path.read_text(encoding='utf8')).group(1).replace('#{accountId}','123')
        query(database,f"CREATE TABLE {table}(id bigint PRIMARY KEY,tenant_id bigint,deleted bit DEFAULT 0,version int DEFAULT 0,status varchar(30),account_ids_json JSON); INSERT INTO {table}(id,tenant_id,status,account_ids_json) VALUES (1,1,'DIRECTOR_REVIEW',JSON_ARRAY(123,456)),(2,1,'DIRECTOR_REVIEW',JSON_ARRAY(123)),(3,1,'DIRECTOR_REVIEW',JSON_ARRAY(456)),(4,2,'DIRECTOR_REVIEW',JSON_ARRAY(123)),(5,1,'DIRECTOR_REVIEW',JSON_ARRAY('123',123,456));")
        query(database,sql)
        result=query(database,f'SELECT id,tenant_id,status,CAST(account_ids_json AS CHAR),version FROM {table} ORDER BY id;')
        rows=[line.split('\t') for line in result.strip().splitlines()]
        assert rows[0][3:] == ['[456]','1'],rows
        assert rows[1][3:] == ['[]','1'],rows
        assert rows[2][3:] == ['[456]','0'],rows
        assert rows[3][3:] == ['[123]','0'],rows
        assert rows[4][3:] == ['["123", 456]','1'],rows
        if table=='zsjos_production_ticket':
            assert rows[0][2]=='DIRECTOR_REVIEW' and rows[1][2]=='cancelled',rows
        query(database,sql)
        assert query(database,f'SELECT id,tenant_id,status,CAST(account_ids_json AS CHAR),version FROM {table} ORDER BY id;')==result
    print('PASS numeric removal, retained sibling, empty container, other tenant, missing target and replay', database)
if __name__=='__main__': main()
