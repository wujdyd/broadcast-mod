# 构建脚本:使用仓库内自带的 JDK17 + Gradle 8.1.1
# 用法:  powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root 'tools\jdk17\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# 本机内存有限(8GB),压低 Gradle 进程占用;若构建报 OOM 可调大
$env:GRADLE_OPTS = '-Xmx700m -XX:MaxMetaspaceSize=384m -XX:CompressedClassSpaceSize=128m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -Dfile.encoding=UTF-8'
Set-Location $root
$log = Join-Path $root 'tools\build.log'
"BUILD START $(Get-Date -Format o)" | Out-File -FilePath $log -Encoding utf8
& "$root\tools\gradle-8.1.1\bin\gradle.bat" build --no-daemon --console=plain 2>&1 |
    Out-File -FilePath $log -Append -Encoding utf8
$code = $LASTEXITCODE
"EXIT=$code" | Out-File -FilePath $log -Append -Encoding utf8
exit $code
