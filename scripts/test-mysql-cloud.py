#!/usr/bin/env python3
"""Cloud validation helper, credentials remain in process environment only."""
from pathlib import Path
import os,subprocess
config=Path(os.environ['MYSQL_TEST_ENV_FILE'])
values=dict(line.split('=',1) for line in config.read_text().splitlines() if '=' in line)
env=os.environ.copy();env.update({'TEST_DATABASE_URL':os.environ['TEST_DATABASE_URL'],'DATABASE_USER':values['MYSQL_USER'],'DATABASE_PASSWORD':values['MYSQL_PASSWORD']})
command=[os.environ.get('MAVEN_EXECUTABLE','./mvnw'),'-Pcloud-ecj','-f','server/pom.xml','test']
if os.environ.get('MAVEN_SETTINGS'):command[1:1]=['-s',os.environ['MAVEN_SETTINGS']]
raise SystemExit(subprocess.run(command,env=env).returncode)
