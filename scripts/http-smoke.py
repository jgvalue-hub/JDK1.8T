#!/usr/bin/env python3
"""로컬 개발 환경 전용: 가상 자료를 생성하고 일부는 영속성 검증용으로 남깁니다."""
import urllib.request, urllib.parse, urllib.error, http.cookiejar, re, json, sys, time
BASE=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:18080/workhub'
report=[]
class Client:
 def __init__(self,user):
  self.jar=http.cookiejar.CookieJar();self.opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(self.jar));self.get('/login');before=self.cookie();self.post('/login',{'username':user,'password':'DevOnly!2026pass'});assert self.url.endswith('/reports');assert before!=self.cookie();report.append(user+' session fixation protection PASS')
 def cookie(self):return next((c.value for c in self.jar if c.name=='JSESSIONID'),None)
 def request(self,path,data=None):
  try:
   r=self.opener.open(BASE+path,data=urllib.parse.urlencode(data).encode() if data is not None else None,timeout=30);code=r.status
  except urllib.error.HTTPError as e:r=e;code=e.code
  self.url=r.url;self.body=r.read().decode();return code
 def get(self,path):return self.request(path)
 def post(self,path,data,csrf=True):
  if csrf:
   token=re.search(r'name="_csrf" value="([^"]+)"',self.body)
   assert token,'No CSRF token at '+self.url
   data=dict(data,_csrf=token.group(1))
  return self.request(path,data)
 def ok(self,path):
  assert self.get(path)==200,(path,self.body[:400]);assert 'Exception' not in self.body;report.append('GET '+path+' 200 JSP PASS')
if __name__ == '__main__':
 a=Client('admin');u=Client('user');m=Client('manager');o=Client('other')
 for path in ['/login','/dashboard','/profile','/users','/users/edit','/departments','/clients','/clients/edit','/projects','/projects/edit','/projects/1','/tasks','/tasks/edit','/tasks/1','/approvals','/approvals/edit','/inbox','/notices','/audit']:
  a.ok(path)
 u.ok('/approvals/edit');assert u.post('/approvals/save',{'name':'HTTP 승인 요청','description':'실제 HTTP 상태 전이 확인','approver_id':'2'})==200
 assert 'HTTP 승인 요청' in u.body
 id=re.search(r'/approvals/(\d+)',u.url).group(1);a.ok('/approvals/'+id)
 assert o.get('/approvals/'+id)==403
 u.get('/approvals/'+id);assert u.post('/approvals/'+id+'/transition',{'version':'0','action':'submit'})==200
 assert '승인대기' in u.body
 m.get('/approvals/'+id);assert m.post('/approvals/'+id+'/transition',{'version':'1','action':'reject','note':'일정을 다시 확인하세요.'})==200
 u.get('/approvals/edit?id='+id);assert u.post('/approvals/save',{'id':id,'version':'2','name':'HTTP 승인 요청 수정','description':'일정 수정 완료','approver_id':'2'})==200
 assert u.post('/approvals/'+id+'/transition',{'version':'3','action':'submit'})==200
 m.get('/approvals/'+id);assert m.post('/approvals/'+id+'/transition',{'version':'4','action':'approve','note':'확인 완료'})==200
 assert m.post('/approvals/'+id+'/transition',{'version':'4','action':'reject','note':'중복 요청'})==409
 report.append('approval DRAFT→PENDING→REJECTED→DRAFT→PENDING→APPROVED; duplicate 409; other user 403 PASS')
 a.get('/clients/edit');stamp=str(int(time.time()));payload='<script>alert("xss")</script>'
 assert a.post('/clients/save',{'name':'HTTP-'+stamp,'description':payload,'contact':'test@example.test'})==200
 assert a.get('/clients?q=HTTP-'+stamp)==200
 cid=re.search(r'/clients/edit\?id=(\d+)',a.body).group(1)
 a.get('/clients/edit?id='+cid);assert payload not in a.body and '&lt;script&gt;' in a.body
 assert a.post('/clients/save',{'id':cid,'version':'0','name':'HTTP-'+stamp,'description':'영속성 검사','contact':'updated@example.test'})==200
 report.append('client create/read/update + stored XSS escaping PASS')
 u.get('/tasks');assert u.post('/tasks/save',{'name':'no csrf'},False)==403
 assert u.get('/users')==403
 u.get('/tasks');assert u.post('/tasks/save',{'id':'1','name':'valid'})==400
 # 다른 담당자는 현재 업무를 수정할 수 없다.
 o.get('/tasks');assert o.post('/tasks/save',{'id':'1','name':'forged'})==403
 report.append('CSRF 403; admin route 403; record ownership 403; validation 400 PASS')
 a.get('/clients');assert a.post('/clients/'+cid+'/delete',{'version':'1'})==200
 assert a.get('/clients/edit?id='+cid)==404
 report.append('client delete + subsequent 404 PASS')
 u.get('/dashboard');assert u.post('/logout',{})==200;assert '/login' in u.url
 assert u.get('/tasks')==200 and '/login' in u.url
 report.append('POST logout invalidates session PASS')
 result={'base':BASE,'checks':report,'persisted_approval_id':id,'result':'PASS'}
 print(json.dumps(result,ensure_ascii=False,indent=2))
