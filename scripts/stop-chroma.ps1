$ErrorActionPreference = "Stop"

$connections = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue

if (-not $connections) {
    Write-Host "Chroma is not listening on port 8000."
    exit 0
}

$connections |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
        Write-Host "Stopping Chroma process $_"
        Stop-Process -Id $_ -Force
    }
