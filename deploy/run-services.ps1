# Start mall-swarm core services (dev profile), pointing at the isolated-port infra
# under E:\Mall_Test\deploy. Infra endpoints are overridden via OS env vars
# (OS env outranks application.yml and Nacos config) so mall-swarm source is untouched.
$ErrorActionPreference = 'Stop'
$root   = 'E:\Mall_Test\mall-swarm'
$logDir = 'E:\Mall_Test\deploy\logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# ---- remapped infrastructure endpoints ----
$env:SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR = '127.0.0.1:18848'
$env:SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR    = '127.0.0.1:18848'
$env:SPRING_DATASOURCE_URL    = 'jdbc:mysql://127.0.0.1:23306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false'
$env:SPRING_DATA_REDIS_HOST   = '127.0.0.1'
$env:SPRING_DATA_REDIS_PORT   = '16379'
$env:SPRING_DATA_MONGODB_HOST = '127.0.0.1'
$env:SPRING_DATA_MONGODB_PORT = '27018'
$env:SPRING_RABBITMQ_HOST     = '127.0.0.1'
$env:SPRING_RABBITMQ_PORT     = '5673'
$env:SPRING_ELASTICSEARCH_URIS = '127.0.0.1:19200'

# per-service server.port overrides (default ports that clash with other local
# stacks are remapped; gateway entry is unchanged because routing is by Nacos service name)
$portMap = @{ 'mall-admin' = 18080 }   # 8080 taken by alsys-backend

function Start-Svc($name) {
    $jar = Join-Path $root "$name\target\$name-1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { Write-Warning "JAR not found: $jar (build first)"; return }
    $svcArgs = @('-Xmx512m', '-jar', $jar, '--spring.profiles.active=dev')
    if ($portMap.ContainsKey($name)) { $svcArgs += "--server.port=$($portMap[$name])" }
    $p = Start-Process -FilePath 'java' -ArgumentList $svcArgs `
        -RedirectStandardOutput "$logDir\$name.log" `
        -RedirectStandardError  "$logDir\$name.err.log" `
        -WindowStyle Hidden -PassThru
    Set-Content "$logDir\$name.pid" $p.Id
    $shownPort = if ($portMap.ContainsKey($name)) { $portMap[$name] } else { 'default' }
    Write-Output ("started {0,-13} pid={1} port={2}" -f $name, $p.Id, $shownPort)
}

# business services first (register to Nacos), gateway last
Start-Svc 'mall-admin';  Start-Sleep -Seconds 6
Start-Svc 'mall-portal'; Start-Sleep -Seconds 6
Start-Svc 'mall-search'; Start-Sleep -Seconds 6
Start-Svc 'mall-auth';   Start-Sleep -Seconds 6
Start-Svc 'mall-gateway'
Write-Output ("logs: {0}\*.log  -- registration completes in ~30-60s" -f $logDir)
