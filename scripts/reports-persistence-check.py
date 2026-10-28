#!/usr/bin/env python3
"""Run after a full DEV server restart, using the preceding HTTP result's file database."""
import importlib.util,sys,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('reports_http',Path(__file__).with_name('reports-http-check.py'));module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
source=Path(sys.argv[2] if len(sys.argv)>2 else 'docs/reports-http-results.json')
evidence=json.loads(source.read_text());saved=evidence['persisted'];a=module.Client('user')
assert a.get('/reports/'+saved['code']+'/'+saved['id'])==200
assert saved['title'] in a.body and saved['decision'] in a.body and '승인 · v9' in a.body
assert '&lt;script&gt;invalid-example&lt;/script&gt;' in a.body
result={'result':'PASS','base':module.legacy.BASE,'report_id':saved['id'],'checks':['approved title and decision note survive full process restart','approved version 9 retained','underlying input record and escaped memo retained']}
Path(sys.argv[3] if len(sys.argv)>3 else 'docs/reports-persistence-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print('PASS: report restart persistence')
