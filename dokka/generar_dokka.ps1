# Script para generar la documentación de Dokka y abrir el navegador

# Navegar a la carpeta repo
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoPath = Join-Path -Path (Split-Path -Parent $scriptPath) -ChildPath "repo"
Set-Location -Path $repoPath

Write-Host "`n=== Cerrando procesos y limpiando documentación ===`n" -ForegroundColor Yellow

# Cerrar cualquier proceso de Python que pueda estar ejecutando el servidor
Get-Process | Where-Object {$_.Name -eq 'python' -and $_.CommandLine -like '*http.server*'} | Stop-Process -Force -ErrorAction SilentlyContinue

# Esperar a que los procesos se cierren
Start-Sleep -Seconds 2

# Intentar limpiar la documentación anterior de forma segura
try {
    if (Test-Path ".\app\build\dokka") {
        Remove-Item -Path ".\app\build\dokka" -Recurse -Force -ErrorAction SilentlyContinue
    }
} catch {
    Write-Host "Nota: Algunos archivos no se pudieron eliminar, pero esto no afectará la generación de nueva documentación" -ForegroundColor Yellow
}

Write-Host "`n=== Generando nueva documentación de Dokka ===`n" -ForegroundColor Green

# Ejecutar el comando para generar la documentación y levantar el servidor
# Usar --continue para ignorar errores no críticos
.\gradlew :app:serveDocs --continue

# El comando serveDocs ya incluye la apertura del navegador automáticamente
Write-Host "`nSi el navegador no se abre automáticamente, visita: http://localhost:8080" -ForegroundColor Cyan 