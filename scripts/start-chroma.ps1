$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Chroma = Join-Path $Root ".venv-chroma\Scripts\chroma.exe"
$DataDir = Join-Path $Root "chroma-data"

if (-not (Test-Path $Chroma)) {
    throw "Chroma CLI not found. Expected: $Chroma"
}

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

Write-Host "Starting Chroma at http://localhost:8000"
Write-Host "Data directory: $DataDir"

& $Chroma run --host 0.0.0.0 --port 8000 --path $DataDir
