#!/usr/bin/env python3
from playwright.sync_api import sync_playwright
from pathlib import Path
import json,sys
base=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:18080/workhub'
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1440,'height':1000})
 errors=[]
 page.on('pageerror',lambda err:errors.append(str(err)))
 page.goto(base+'/login');page.get_by_label('아이디',exact=True).fill('admin');page.get_by_label('비밀번호',exact=True).fill('DevOnly!2026pass');page.get_by_role('button',name='로그인',exact=True).click();page.wait_for_url('**/dashboard')
 assert page.locator('h1').inner_text()=='대시보드'
 assert page.locator('body').evaluate('(el)=>getComputedStyle(el).backgroundColor')=='rgb(245, 247, 251)'
 page.screenshot(path='docs/dashboard-desktop.png',full_page=True)
 page.goto(base+'/tasks/edit');page.locator('input[name="name"]').fill('브라우저 등록 업무');page.locator('textarea[name="description"]').fill('실제 브라우저에서 폼을 제출했습니다.');page.locator('input[name="due_date"]').fill('2027-01-10');page.get_by_role('button',name='저장',exact=True).click();assert '브라우저 등록 업무' in page.locator('main').inner_text()
 page.set_viewport_size({'width':390,'height':844});page.goto(base+'/dashboard');page.screenshot(path='docs/dashboard-mobile.png',full_page=True)
 assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
 assert not errors,errors
 Path('docs/browser-results.json').write_text(json.dumps({'result':'PASS','desktop':'1440x1000','mobile':'390x844','checks':['real login','CSS loaded','task form submission and detail','mobile no horizontal overflow','no browser JS errors']},ensure_ascii=False,indent=2))
 browser.close()
