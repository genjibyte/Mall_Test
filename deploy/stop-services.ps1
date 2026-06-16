# Stop mall-swarm services started by run-services.ps1 (by PID file).
$logDir = 'E:\Mall_Test\deploy\logs'
Get-ChildItem "$logDir\*.pid" -ErrorAction SilentlyContinue | ForEach-Object {
    $name = $_.BaseName
    $procId = Get-Content $_.FullName
    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        Write-Output ("stopped {0,-13} pid={1}" -f $name, $procId)
    } catch {
        Write-Output ("not running {0,-13} pid={1}" -f $name, $procId)
    }
    Remove-Item $_.FullName -ErrorAction SilentlyContinue
}
