#!/usr/bin/env python3
import importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('http_smoke',Path(__file__).with_name('http-smoke.py'));module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
a=module.Client('admin')
id=json.loads(Path('docs/http-smoke-results.json').read_text())['persisted_approval_id']
assert a.get('/approvals/'+id)==200
assert 'HTTP 승인 요청 수정' in a.body and '확인 완료' in a.body and 'APPROVED' not in a.body # 표시 레이블은 한국어
result={'result':'PASS','approval_id':id,'check':'After full Tomcat stop/start, approved request title and decision note persist in file H2'}
Path('docs/persistence-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(result)
