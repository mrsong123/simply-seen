@echo off
setlocal
where mvn >nul 2>&1
if errorlevel 1 goto bootstrap
call mvn %*
exit /b %ERRORLEVEL%

:bootstrap
if not defined SUJIAN_TOOLS_DIR set "SUJIAN_TOOLS_DIR=%~dp0.tools"
set "SUJIAN_MAVEN_HOME=%SUJIAN_TOOLS_DIR%\apache-maven-3.9.11"
if exist "%SUJIAN_MAVEN_HOME%\bin\mvn.cmd" goto run
powershell.exe -NoLogo -NoProfile -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $tools=$env:SUJIAN_TOOLS_DIR; New-Item -ItemType Directory -Path $tools -Force | Out-Null; if(Test-Path -LiteralPath $env:SUJIAN_MAVEN_HOME){throw 'Incomplete Maven cache. Remove the incomplete apache-maven-3.9.11 cache directory and retry.'}; $archive=Join-Path $tools ('maven-'+[guid]::NewGuid().ToString()+'.zip'); try { $options=@{}; if($env:HTTPS_PROXY){$options['Proxy']=$env:HTTPS_PROXY}; Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile $archive @options; if((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne '03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076'){throw 'Maven SHA-512 verification failed'}; Expand-Archive -LiteralPath $archive -DestinationPath $tools } finally { if(Test-Path -LiteralPath $archive){Remove-Item -LiteralPath $archive -Force} }"
if errorlevel 1 exit /b %ERRORLEVEL%

:run
call "%SUJIAN_MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
