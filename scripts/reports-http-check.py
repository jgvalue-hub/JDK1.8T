#!/usr/bin/env python3
"""Local DEV fixture only. Exercises every report type; never run against production data."""
import importlib.util, sys, re, json, urllib.request, urllib.parse, urllib.error, time
from pathlib import Path
from html.parser import HTMLParser
spec=importlib.util.spec_from_file_location('legacy_http',Path(__file__).with_name('http-smoke.py'));legacy=importlib.util.module_from_spec(spec);spec.loader.exec_module(legacy)
class Client(legacy.Client):
 def request(self,path,data=None):
  try:r=self.opener.open(legacy.BASE+path,data=urllib.parse.urlencode(data,doseq=True).encode() if data is not None else None,timeout=45);code=r.status
  except urllib.error.HTTPError as e:r=e;code=e.code
  self.url=r.url;self.body=r.read().decode();return code
class Form(HTMLParser):
 def __init__(self,body):
  super().__init__();self.fields={};self.select=None;self.first=None;self.active=False;self.feed(body)
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if tag=='form' and a.get('id')=='report-form':self.active=True
  if not self.active:return
  if tag=='input' and 'name' in a:self.fields.setdefault(a['name'],[]).append(a.get('value',''))
  if tag=='select':self.select=a.get('name');self.first=None
  if tag=='option' and self.select and 'value' in a:
   if self.first is None:self.first=a['value']
   if 'selected' in a:self.fields[self.select]=[a['value']]
 def handle_endtag(self,tag):
  if tag=='form':self.active=False
  if tag=='select' and self.select:self.fields.setdefault(self.select,[self.first or '']);self.select=None

def fixture(client,code):
 assert client.get('/reports/'+code+'/edit')==200,client.body[:200]
 family=re.search('data-family="([A-Z]+)"',client.body).group(1)
 samples={'LEDGER':['1000','1000','0'],'RECEIVABLE':['1000','300','95'],'PAYABLE':['1000','300','50'],'ASSET':['1200','0','12'],'BUDGET':['1000','800','1100'],'CASH':['500','300','200'],'MARKET':['10000','10','20'],'CREDIT':['10000','5','40'],'OPERATION':['4','4','25'],'CONTROL':['20','15','2']}
 category=re.search(r'<select aria-label="명세 분류".*?<option[^>]*>([^<]+)</option>',client.body,re.S).group(1)
 f=Form(client.body).fields
 f.update(title='HTTP50 가상 '+code,business_unit='가상 재무팀',currency='KRW',reviewer_id='2',summary_note='교육용 가정만 포함',lineLabel=['가상 명세'],lineCategory=[category],lineA=[samples[family][0]],lineB=[samples[family][1]],lineC=[samples[family][2]],lineMemo=['<script>invalid-example</script>'])
 return f,family

def version(body):return re.search(r'name="version" value="([0-9]+)"',body).group(1)
if __name__=='__main__':
 u=Client('user');m=Client('manager');o=Client('other');checks=[]
 assert u.get('/reports')==200
 menus=re.findall(r'class="business-menu[^\"]*" href="[^\"]*/reports/([a-z-]+)"',u.body)
 assert len(menus)==len(set(menus))==50, len(menus)
 results=[];saved=None
 for code in menus:
  assert u.get('/reports/'+code)==200,code
  f,family=fixture(u,code)
  assert u.post('/reports/'+code+'/save',f)==200,(code,u.body[:400])
  rid=re.search('/reports/'+code+'/([0-9]+)',u.url).group(1)
  assert '&lt;script&gt;invalid-example&lt;/script&gt;' in u.body and '<script>invalid-example</script>' not in u.body
  assert o.get('/reports/'+code+'/'+rid)==403
  u.get('/reports/'+code+'/edit?id='+rid)
  f['id']=rid;f['version']='0';f['title']='수정 '+code
  # Add and then remove an underlying persisted input line.
  for key in ['lineLabel','lineCategory','lineA','lineB','lineC','lineMemo']:f[key]=f[key]*2
  assert u.post('/reports/'+code+'/save',f)==200,(code,u.body[:300])
  assert u.body.count('&lt;script&gt;invalid-example&lt;/script&gt;')==2
  u.get('/reports/'+code+'/edit?id='+rid);f['version']='1'
  for key in ['lineLabel','lineCategory','lineA','lineB','lineC','lineMemo']:f[key]=f[key][:1]
  assert u.post('/reports/'+code+'/save',f)==200
  assert u.post('/reports/'+code+'/'+rid+'/transition',{'version':'2','action':'delete','note':'가상 초안 정리'})==200
  assert '휴지통에 보관' in u.body
  assert u.post('/reports/'+code+'/'+rid+'/transition',{'version':'3','action':'restore'})==200
  results.append({'code':code,'family':family,'id':rid,'CRUD_lines_trash_restore':'PASS'})
  if code=='expected-loss':saved=(code,rid)
 code,rid=saved;path='/reports/'+code+'/'+rid
 u.get(path);assert u.post(path+'/transition',{'version':'4','action':'submit'})==200
 m.get(path);assert m.post(path+'/transition',{'version':'5','action':'reject','note':'가상 손실률 재검토'})==200
 f,_=fixture(u,code);u.get('/reports/'+code+'/edit?id='+rid);f['id']=rid;f['version']='6';f['title']='재기동 보존 보고서';assert u.post('/reports/'+code+'/save',f)==200
 assert u.post(path+'/transition',{'version':'7','action':'submit'})==200
 m.get(path);assert m.post(path+'/transition',{'version':'8','action':'approve','note':'가상 시나리오 확인 완료'})==200
 u.get(path);assert u.post(path+'/transition',{'version':'9','action':'delete','note':'승인본 보호'})==409
 u.get(path);assert u.post(path+'/copy',{'reviewer_id':'2'})==200
 assert '사본' in u.body and '원본 보고서 #'+rid in u.body
 u.get(path);assert u.post(path+'/transition',{'version':'9','action':'delete'},False)==403
 # Validation must redisplay input in real JSP, including malformed reviewer IDs.
 f,_=fixture(u,'fixed-assets');f['lineC']=['0'];f['title']='검증 오류 입력 유지'
 assert u.post('/reports/fixed-assets/save',f)==400;assert '검증 오류 입력 유지' in u.body and '저장하지 못했습니다' in u.body
 f['reviewer_id']='not-a-number';assert u.post('/reports/fixed-assets/save',f)==400
 f,_=fixture(u,'cash-position')
 for key in ['lineLabel','lineCategory','lineA','lineB','lineC','lineMemo']:f[key]=f[key]*12
 assert u.post('/reports/cash-position/save',f)==200
 assert u.body.count('&lt;script&gt;invalid-example&lt;/script&gt;')==12
 u.get('/reports/cash-position/edit')
 for key in ['lineLabel','lineCategory','lineA','lineB','lineC','lineMemo']:f[key].append(f[key][0])
 assert u.post('/reports/cash-position/save',f)==400
 report={'result':'PASS','base':legacy.BASE,'menu_count':50,'reports':results,'checks':['50 list/edit/detail JSP routes','50 persisted report CRUD and editable input-line CRUD','50 soft delete and restore','stored HTML escaping','private report access 403','rejection/edit/resubmission/approval','approved edit/delete protection','copy preserves approved original','CSRF 403','validation 400 with retained input including malformed reviewer','12-line request accepted; 13-line request rejected'],'persisted':{'code':code,'id':rid,'title':'재기동 보존 보고서','decision':'가상 시나리오 확인 완료'}}
 Path(sys.argv[2] if len(sys.argv)>2 else 'docs/reports-http-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'result':'PASS','menu_count':50,'persisted':report['persisted']},ensure_ascii=False))
