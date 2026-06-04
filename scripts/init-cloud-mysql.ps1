$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Schema = Join-Path $Root "backend\src\main\resources\db\schema.sql"
$MysqlCommand = Get-Command "mysql" -ErrorAction SilentlyContinue
$Mysql = if ($MysqlCommand) {
    $MysqlCommand.Source
} else {
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
}

if (-not (Test-Path -LiteralPath $Mysql)) {
    throw "mysql.exe not found. Add MySQL 8.0 bin to PATH or install MySQL client."
}

$HostName = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "119.29.163.90" }
$Port = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
$Database = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "contract_agent" }
$User = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }

if (-not $env:MYSQL_PWD) {
    throw "Please set MYSQL_PWD before running this script."
}

Write-Host "Creating database $Database on ${HostName}:${Port} ..."
& $Mysql -h $HostName -P $Port -u $User -e "CREATE DATABASE IF NOT EXISTS $Database DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

Write-Host "Importing schema: $Schema"
Get-Content $Schema | & $Mysql -h $HostName -P $Port -u $User $Database

Write-Host "Tables:"
& $Mysql -h $HostName -P $Port -u $User $Database -e "SHOW TABLES;"

Write-Host "Cloud MySQL initialization finished."
