#!/usr/bin/env python3
"""Check all four Gradle WAR layouts after production and -Pdev builds."""
import json, zipfile
from pathlib import Path
results = []
for name in ['workhub.war', 'workhub-boot.war', 'workhub-dev.war', 'workhub-dev-boot.war']:
    path = Path('build/libs') / name
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        standard = [n for n in names if n.startswith('WEB-INF/lib/') and n.endswith('.jar')]
        provided = [n for n in names if n.startswith('WEB-INF/lib-provided/') and n.endswith('.jar')]
        dev = '-dev' in name
        executable = '-boot' in name
        assert any('h2-2.2.224.jar' in n for n in standard) == dev
        assert not any(any(tag in n for tag in ['tomcat-', 'javax.servlet-api-', 'ecj-']) for n in standard)
        assert bool(provided) == executable
        for module, version in [('spring-core','5.3.34'), ('spring-webmvc','5.3.34'), ('spring-boot','2.7.18'), ('spring-security-web','5.8.16')]:
            assert 'WEB-INF/lib/' + module + '-' + version + '.jar' in names
        assert 'WEB-INF/classes/kr/co/workhub/WorkhubApplication.class' in names
        assert 'WEB-INF/classes/kr/co/workhub/Initializer.class' not in names
        assert 'WEB-INF/classes/kr/co/workhub/SecurityInitializer.class' not in names
        if executable:
            manifest = archive.read('META-INF/MANIFEST.MF').decode()
            assert 'Main-Class: org.springframework.boot.loader.WarLauncher' in manifest
            assert 'Start-Class: kr.co.workhub.WorkhubApplication' in manifest
        results.append({'file': name, 'bytes': path.stat().st_size, 'h2': dev,
                        'standard_libraries': len(standard), 'provided_libraries': len(provided), 'result':'PASS'})
print(json.dumps(results, indent=2))
