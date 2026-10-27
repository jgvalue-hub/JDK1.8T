#!/usr/bin/env python3
import zipfile,io,struct,sys,json
from pathlib import Path
war=Path(sys.argv[1] if len(sys.argv)>1 else 'target/workhub.war');bad=[];count=0;skipped=0;libs=[]
def scan(z,label):
 global count,skipped
 for name in z.namelist():
  if name.endswith('.class'):
   if name.startswith('META-INF/versions/') or name.endswith('module-info.class'):skipped+=1;continue
   data=z.read(name);major=struct.unpack('>H',data[6:8])[0];count+=1
   if major>52:bad.append([label,name,major])
with zipfile.ZipFile(war) as z:
 scan(z,war.name)
 for n in z.namelist():
  if n.startswith('WEB-INF/lib/') and n.endswith('.jar'):
   libs.append(n.split('/')[-1]);scan(zipfile.ZipFile(io.BytesIO(z.read(n))),n)
result={'war':war.name,'baseline':'Java 8 class version <=52','class_count':count,'ignored_multi_release_or_module_info':skipped,'incompatible':bad,'libraries':libs,'result':'FAIL' if bad else 'PASS'}
print(json.dumps(result,indent=2));sys.exit(bool(bad))
