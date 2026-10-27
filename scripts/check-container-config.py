#!/usr/bin/env python3
"""Validate fail-closed startup without credentials, deployment, or opening any port."""
import os, subprocess, json, xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parent.parent
base={'WORKHUB_ENABLE_DEMO':'true','WORKHUB_HTTPS_PROXY':'true','WORKHUB_PUBLIC_HOST':'preview.example.test'}
cases=[
 ('explicit demo opt-in',{},'WORKHUB_ENABLE_DEMO'),
 ('HTTPS proxy confirmation',{'WORKHUB_ENABLE_DEMO':'true'},'HTTPS'),
 ('public hostname',{'WORKHUB_ENABLE_DEMO':'true','WORKHUB_HTTPS_PROXY':'true'},'PUBLIC_HOST'),
 ('secret required',base,'DEMO_PASSWORD'),
 ('short secret',dict(base,WORKHUB_DEMO_PASSWORD='short'),'24-72'),
 ('port validation',dict(base,PORT='invalid'),'PORT'),
 ('hostname injection rejected',dict(base,WORKHUB_PUBLIC_HOST='host -Dunsafe=true'),'PUBLIC_HOST'),
 ('Render hostname fallback',{'WORKHUB_ENABLE_DEMO':'true','WORKHUB_HTTPS_PROXY':'true','RENDER_EXTERNAL_HOSTNAME':'preview.example.test'},'DEMO_PASSWORD')]
checks=[]
clean={k:v for k,v in os.environ.items() if not k.startswith('WORKHUB_') and k not in ['PORT','RENDER_EXTERNAL_HOSTNAME']}
for title,values,message in cases:
 r=subprocess.run(['sh',str(root/'deploy/container-entrypoint.sh')],env=dict(clean,**values),capture_output=True,text=True,timeout=5)
 assert r.returncode==64 and message in r.stderr,(title,r.returncode,r.stderr)
 checks.append(title)
server=ET.parse(root/'deploy/server.xml');connector=server.find('.//Connector')
assert connector.get('scheme')=='https' and connector.get('secure')=='true'
assert connector.get('port')=='${workhub.port}' and connector.get('proxyName')=='${workhub.publicHost}'
assert server.getroot().get('port')=='-1'
context=ET.parse(root/'deploy/context.xml');assert context.find('.//CookieProcessor').get('sameSiteCookies')=='lax'
print(json.dumps({'result':'PASS','negative_startup_checks':checks,'xml':'PASS','network_or_deployment_performed':False},indent=2))
