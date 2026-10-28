#!/usr/bin/env python3
"""Chromium DEV-only checks: grouped navigation, input lines, validation and mobile layout."""
from playwright.sync_api import sync_playwright
from pathlib import Path
import sys,json
base=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:18083/workhub'
checks=[]
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1440,'height':1000});errors=[]
 page.on('pageerror',lambda error:errors.append(str(error)))
 page.goto(base+'/login');page.get_by_label('아이디',exact=True).fill('user');page.get_by_label('비밀번호',exact=True).fill('DevOnly!2026pass');page.get_by_role('button',name='로그인',exact=True).click();page.wait_for_url('**/reports')
 page.goto(base+'/reports');assert page.locator('.business-menu').count()==50;assert page.locator('.menu-group').count()==10
 assert page.locator('.menu-card').count()==50
 page.locator('#menu-search').fill('신용');assert page.locator('.business-menu:visible').count()==2
 page.locator('#menu-search').fill('없는메뉴');assert page.locator('#menu-empty').is_visible()
 page.locator('#menu-search').fill('');checks.append('50 unique main entries; ten groups; live menu filtering and empty state')
 modules=['journal-vouchers','receivable-aging','payable-aging','fixed-assets','budget-performance','cash-position','fx-risk','expected-loss','risk-register','control-testing']
 for code in modules:
  page.goto(base+'/reports/'+code+'/edit');page.get_by_role('button',name='빈 명세에 데모 예시').click()
  page.locator('[name=business_unit]').fill('브라우저 가상 재무팀');page.locator('[name=reviewer_id]').select_option('2')
  page.get_by_role('button',name='+ 행 추가',exact=True).click();assert page.locator('#report-lines tr').count()==2
  page.locator('#report-lines tr').last.get_by_role('button',name='행 삭제').click();assert page.locator('#report-lines tr').count()==1
  page.get_by_role('button',name='초안 저장',exact=True).click();page.wait_for_url('**/reports/'+code+'/*?saved');assert page.locator('#report-result').is_visible()
  assert '가상 데모 항목' in page.locator('main').inner_text()
 checks.append('ten domain editors: demo population, add/remove line, save, server calculation and detail')
 page.goto(base+'/reports/fixed-assets/edit');page.get_by_role('button',name='빈 명세에 데모 예시').click();page.locator('[name=business_unit]').fill('오류 보존 확인');page.locator('[name=reviewer_id]').select_option('2');page.locator('[name=lineC]').fill('0');page.get_by_role('button',name='초안 저장',exact=True).click()
 assert '내용연수' in page.locator('[role=alert]').inner_text();assert page.locator('[name=business_unit]').input_value()=='오류 보존 확인';checks.append('server validation error retains entered values')
 page.set_viewport_size({'width':390,'height':844})
 for path in ['/reports','/reports/fx-risk','/reports/fx-risk/edit']:
  page.goto(base+path);assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'),path
 page.locator('#menu-search').fill('시장');assert page.locator('.business-menu:visible').count()==1
 page.locator('.business-menu:visible').click();page.wait_for_url('**/reports/market-stress')
 page.goto(base+'/reports/fx-risk/edit');page.get_by_role('button',name='빈 명세에 데모 예시').click();page.locator('[name=business_unit]').fill('모바일 가상팀');page.locator('[name=reviewer_id]').select_option('2');page.get_by_role('button',name='초안 저장',exact=True).click();page.wait_for_url('**?saved')
 assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
 checks.append('390x844 mobile index/list/editor/detail; no document overflow; filtered menu navigation and saved report')
 assert not errors,errors
 Path('docs/reports-browser-results.json').write_text(json.dumps({'result':'PASS','desktop':'1440x1000','mobile':'390x844','checks':checks,'javascript_errors':errors},ensure_ascii=False,indent=2))
 browser.close()
print('PASS: report browser and mobile checks')
