$ErrorActionPreference = "Stop"

$serviceName = "MySQL97"
$installDir = "C:\Users\amc\Downloads\mysql-9.7.0-winx64\mysql-9.7.0-winx64"
$programDataDir = "C:\ProgramData\MySQL\MySQL Server 9.7"
$mysqlBin = Join-Path $installDir "bin"

function Remove-PathFromUserPath($pathToRemove) {
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if (-not $userPath) {
        return
    }
    $parts = $userPath -split ";" | Where-Object {
        $_ -and $_.Trim() -and ($_.Trim() -ne $pathToRemove)
    }
    [Environment]::SetEnvironmentVariable("Path", ($parts -join ";"), "User")
}

Write-Host "Stopping service $serviceName if it exists..."
$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($service) {
    if ($service.Status -ne "Stopped") {
        Stop-Service -Name $serviceName -Force
        Start-Sleep -Seconds 3
    }
    sc.exe delete $serviceName | Out-Host
}

Write-Host "Stopping remaining mysqld.exe processes from MySQL 9.7 directory..."
Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" |
    Where-Object { $_.ExecutablePath -like "$mysqlBin*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
    }

Write-Host "Removing MySQL 9.7 from user PATH..."
Remove-PathFromUserPath $mysqlBin

$resolvedInstall = Resolve-Path -LiteralPath $installDir -ErrorAction SilentlyContinue
if ($resolvedInstall -and $resolvedInstall.Path -eq $installDir) {
    Write-Host "Removing install directory: $installDir"
    Remove-Item -LiteralPath $installDir -Recurse -Force
}

$resolvedProgramData = Resolve-Path -LiteralPath $programDataDir -ErrorAction SilentlyContinue
if ($resolvedProgramData -and $resolvedProgramData.Path -eq $programDataDir) {
    Write-Host "Removing ProgramData directory: $programDataDir"
    Remove-Item -LiteralPath $programDataDir -Recurse -Force
}

Write-Host "Checking port 3306..."
$listener = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Port 3306 is still in use:"
    $listener | Select-Object LocalAddress, LocalPort, OwningProcess | Format-Table
} else {
    Write-Host "Port 3306 is free."
}

Write-Host "MySQL 9.7 cleanup finished."
