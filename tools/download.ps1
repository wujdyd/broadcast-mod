$ErrorActionPreference = 'Stop'
$root = 'D:\如意aiagent\New folder\tools'
$log = Join-Path $root 'download.log'
function Dl($url, $file) {
  Add-Content $log "GET $url"
  $wc = New-Object System.Net.WebClient
  $wc.DownloadFile($url, $file)
  Add-Content $log ("DONE {0} {1} bytes" -f $file, (Get-Item $file).Length)
}
try {
  Dl 'https://mirrors.cloud.tencent.com/gradle/gradle-8.1.1-bin.zip' "$root\gradle-8.1.1-bin.zip"
  Expand-Archive "$root\gradle-8.1.1-bin.zip" $root -Force
  Add-Content $log 'GRADLE_EXTRACTED'
  Dl 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' "$root\jdk17.zip"
  Expand-Archive "$root\jdk17.zip" "$root\jdk17" -Force
  Add-Content $log 'JDK_EXTRACTED'
  Add-Content $log 'ALL_DONE'
} catch {
  Add-Content $log "ERROR $($_.Exception.Message)"
}
