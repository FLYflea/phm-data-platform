# PHM Platform 服务启动脚本 (PowerShell 版本)
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   PHM Platform 服务启动脚本" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker 状态
Write-Host "[1/6] 检查 Docker 服务状态..." -ForegroundColor Yellow
try {
    $dockerStatus = docker ps --format "table {{.Names}}\t{{.Status}}" | Select-Object -First 5
    if ($dockerStatus) {
        Write-Host "[OK] Docker 正在运行" -ForegroundColor Green
        Write-Host $dockerStatus
    } else {
        Write-Host "[警告] Docker 未运行，请启动 Docker Desktop" -ForegroundColor Red
    }
} catch {
    Write-Host "[错误] Docker 检查失败：$_" -ForegroundColor Red
}
Write-Host ""

# 启动 Gateway
Write-Host "[2/6] 启动 Gateway (8080)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\phm-gateway'; mvn spring-boot:run"
Start-Sleep -Seconds 3
Write-Host "[OK] Gateway 启动中..." -ForegroundColor Green
Write-Host ""

# 启动数据服务层（4 个服务同时启动）
Write-Host "[3/6] 启动数据服务层..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\phm-data-service\data-collection'; mvn spring-boot:run"
Write-Host "  - Collection (8101) 启动中..." -ForegroundColor Green

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\phm-data-service\data-computation'; mvn spring-boot:run"
Write-Host "  - Computation (8102) 启动中..." -ForegroundColor Green

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\phm-data-service\data-storage'; mvn spring-boot:run"
Write-Host "  - Storage (8103) 启动中..." -ForegroundColor Green

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\phm-data-service\data-service'; mvn spring-boot:run"
Write-Host "  - Service (8104) 启动中..." -ForegroundColor Green

Start-Sleep -Seconds 5
Write-Host ""

# 启动前端
Write-Host "[4/6] 启动前端开发服务器 (5173)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\phm-frontend'; npm run dev"
Write-Host "[OK] 前端启动中..." -ForegroundColor Green
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   所有服务启动完成！" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问地址:" -ForegroundColor White
Write-Host "  - 前端页面：http://localhost:5173" -ForegroundColor Cyan
Write-Host "  - Gateway:   http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "服务端口:" -ForegroundColor White
Write-Host "  - Gateway:      8080" -ForegroundColor Gray
Write-Host "  - Collection:   8101" -ForegroundColor Gray
Write-Host "  - Computation:  8102" -ForegroundColor Gray
Write-Host "  - Storage:      8103" -ForegroundColor Gray
Write-Host "  - Service:      8104" -ForegroundColor Gray
Write-Host ""
Write-Host "按任意键关闭此窗口（服务将继续在后台运行）" -ForegroundColor Yellow
