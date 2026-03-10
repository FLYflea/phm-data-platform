# PHM-Platform 端到端测试脚本
# 使用 PowerShell 执行
# 作者：毕业设计测试用例

$baseUrl = "http://localhost:8080"
$collectionUrl = "$baseUrl/api/collection"
$computationUrl = "$baseUrl/api/computation"
$storageUrl = "$baseUrl/api/storage"
$serviceUrl = "$baseUrl/api/service"

Write-Host "========================================" -ForegroundColor Green
Write-Host "PHM-Platform 端到端 API 测试" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# ==================== 测试用例 1：在线采集 → 计算同步 → 存储 → 查询 ====================
Write-Host "【测试用例 1】在线采集 → 计算同步 → 存储 → 查询" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$test1Data = @{
    deviceId = "TEST_DEVICE_001"
    sensorType = "temperature"
    value = 25.5
    timestamp = "2026-03-05T10:30:00"
} | ConvertTo-Json

Write-Host "Step 1: 发送传感器数据到采集层..."
$test1Response = Invoke-RestMethod -Uri "$collectionUrl/sensor" -Method POST -ContentType "application/json" -Body $test1Data
Write-Host "响应: $($test1Response | ConvertTo-Json -Depth 2)"

# 等待数据处理
Start-Sleep -Seconds 2

Write-Host "Step 2: 查询存储的数据..."
$queryParams = @{
    deviceId = "TEST_DEVICE_001"
    start = "2026-03-05T00:00:00Z"
    end = "2026-03-05T23:59:59Z"
}
$queryString = ($queryParams.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
$test1Query = Invoke-RestMethod -Uri "$serviceUrl/query/raw" -Method POST -ContentType "application/json" -Body (@{
    deviceId = "TEST_DEVICE_001"
    startTime = "2026-03-05T00:00:00Z"
    endTime = "2026-03-05T23:59:59Z"
} | ConvertTo-Json)
Write-Host "查询结果: $($test1Query | ConvertTo-Json -Depth 2)"

Write-Host "✅ 测试用例 1 完成" -ForegroundColor Green
Write-Host ""

# ==================== 测试用例 2：批量采集 + Delta 压缩比计算 ====================
Write-Host "【测试用例 2】批量采集 + Delta 压缩比计算" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$batchData = @{
    deviceId = "TEST_DEVICE_002"
    sensorType = "vibration"
    data = @(10.5, 10.8, 11.2, 10.9, 11.5, 11.0, 10.7, 11.3, 10.6, 11.1)
} | ConvertTo-Json

Write-Host "发送批量数据（10条）..."
$test2Response = Invoke-RestMethod -Uri "$collectionUrl/sensor/batch" -Method POST -ContentType "application/json" -Body $batchData
Write-Host "响应: $($test2Response | ConvertTo-Json -Depth 2)"

if ($test2Response.data.compressionRatio -gt 1.0) {
    Write-Host "✅ 压缩比验证通过: $($test2Response.data.compressionRatio)" -ForegroundColor Green
} else {
    Write-Host "⚠️ 压缩比可能异常: $($test2Response.data.compressionRatio)" -ForegroundColor Yellow
}

Write-Host ""

# ==================== 测试用例 3：时间同步算法验证 ====================
Write-Host "【测试用例 3】时间同步算法验证（不确定性同步）" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$syncData = @{
    rawData = @(
        @{ deviceId = "DEV_001"; sensorType = "temp"; value = 25.0; timestamp = "2026-03-05T10:00:00" },
        @{ deviceId = "DEV_001"; sensorType = "temp"; value = 25.5; timestamp = "2026-03-05T10:00:01" },
        @{ deviceId = "DEV_001"; sensorType = "temp"; value = 26.0; timestamp = "2026-03-05T10:00:03" },
        @{ deviceId = "DEV_001"; sensorType = "temp"; value = 26.5; timestamp = "2026-03-05T10:00:04" },
        @{ deviceId = "DEV_001"; sensorType = "temp"; value = 27.0; timestamp = "2026-03-05T10:00:06" }
    )
    kNeighbors = 2
    expectedIntervalMs = 1000
} | ConvertTo-Json -Depth 4

Write-Host "发送乱序时间戳数据..."
$test3Response = Invoke-RestMethod -Uri "$computationUrl/sync/uncertainty" -Method POST -ContentType "application/json" -Body $syncData
Write-Host "响应: $($test3Response | ConvertTo-Json -Depth 3)"

if ($test3Response.data.averageConfidence -gt 0) {
    Write-Host "✅ 同步完成，平均置信度: $($test3Response.data.averageConfidence)" -ForegroundColor Green
}

Write-Host ""

# ==================== 测试用例 4：PDA 数据融合验证 ====================
Write-Host "【测试用例 4】PDA 数据融合验证" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$fusionData = @{
    multiSourceData = @(
        @(
            @{ deviceId = "DEV_001"; sensorType = "pressure"; value = 100.2; timestamp = "2026-03-05T10:00:00" },
            @{ deviceId = "DEV_001"; sensorType = "pressure"; value = 100.5; timestamp = "2026-03-05T10:00:01" }
        ),
        @(
            @{ deviceId = "DEV_001"; sensorType = "pressure"; value = 100.3; timestamp = "2026-03-05T10:00:00" },
            @{ deviceId = "DEV_001"; sensorType = "pressure"; value = 100.4; timestamp = "2026-03-05T10:00:01" }
        )
    )
} | ConvertTo-Json -Depth 5

Write-Host "发送多源传感器数据..."
$test4Response = Invoke-RestMethod -Uri "$computationUrl/fusion/pda" -Method POST -ContentType "application/json" -Body $fusionData
Write-Host "响应: $($test4Response | ConvertTo-Json -Depth 3)"

if ($test4Response.data.fusionResults) {
    Write-Host "✅ 融合完成，结果数量: $($test4Response.data.fusionResults.Count)" -ForegroundColor Green
}

Write-Host ""

# ==================== 测试用例 5：知识图谱构建 → 查询 ====================
Write-Host "【测试用例 5】知识图谱构建 → 查询" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$kgData = @{
    docId = "MAINTENANCE_DOC_001"
    text = "设备EQ001的主轴承发生故障，导致温度传感器TS001读数异常。更换轴承后设备恢复正常。"
    equipmentId = "EQ001"
} | ConvertTo-Json

Write-Host "Step 1: 从文本构建知识图谱..."
$test5Response = Invoke-RestMethod -Uri "$computationUrl/knowledge/build/text" -Method POST -ContentType "application/json" -Body $kgData
Write-Host "响应: $($test5Response | ConvertTo-Json -Depth 2)"

Start-Sleep -Seconds 2

Write-Host "Step 2: 查询知识图谱..."
$test5Query = Invoke-RestMethod -Uri "$storageUrl/graph/equipment/EQ001" -Method GET
Write-Host "查询结果: $($test5Query | ConvertTo-Json -Depth 2)"

if ($test5Query.status -eq "success") {
    Write-Host "✅ 知识图谱构建成功" -ForegroundColor Green
}

Write-Host ""

# ==================== 测试用例 6：故障模式存储 → FMECA 查询 ====================
Write-Host "【测试用例 6】故障模式存储 → FMECA 查询" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$fmecaData = @{
    failureModeId = "FM_001"
    equipmentId = "EQ001"
    componentId = "BEARING_001"
    name = "主轴承磨损"
    description = "主轴承因润滑不足导致磨损"
    causes = @("润滑不足", "负载过大")
    localEffect = "轴承温度升高"
    finalEffect = "设备停机"
    severityClass = 4
    occurrenceLevel = "B"
    detectionLevel = 3
    improvementMeasures = @("定期润滑", "温度监测")
    sourceDocId = "DOC_001"
} | ConvertTo-Json -Depth 3

Write-Host "Step 1: 保存故障模式..."
$test6Response = Invoke-RestMethod -Uri "$storageUrl/fmeca/failure-mode" -Method POST -ContentType "application/json" -Body $fmecaData
Write-Host "响应: $($test6Response | ConvertTo-Json -Depth 2)"

Write-Host "Step 2: 查询故障模式..."
$test6Query = Invoke-RestMethod -Uri "$storageUrl/fmeca/failure-mode/equipment/EQ001" -Method GET
Write-Host "查询结果: $($test6Query | ConvertTo-Json -Depth 2)"

if ($test6Query.count -gt 0) {
    Write-Host "✅ FMECA 数据存储成功" -ForegroundColor Green
}

Write-Host ""

# ==================== 测试用例 7：特征工程验证 ====================
Write-Host "【测试用例 7】特征工程验证" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$featureData = @{
    values = @(10.5, 20.3, 15.8, 25.1, 18.7, 22.4, 19.6, 21.3)
} | ConvertTo-Json

Write-Host "发送数据提取时域特征..."
$test7Response = Invoke-RestMethod -Uri "$computationUrl/feature/time-domain" -Method POST -ContentType "application/json" -Body $featureData
Write-Host "响应: $($test7Response | ConvertTo-Json -Depth 2)"

if ($test7Response.data.mean -and $test7Response.data.stdDev) {
    Write-Host "✅ 特征提取成功 - 均值: $($test7Response.data.mean), 标准差: $($test7Response.data.stdDev)" -ForegroundColor Green
}

Write-Host ""

# ==================== 测试用例 8：维修时间抽取 ====================
Write-Host "【测试用例 8】维修时间抽取" -ForegroundColor Yellow
Write-Host "----------------------------------------"

$maintenanceData = @{
    maintenanceText = "2026年3月5日10点30分开始维修，发现主轴承故障。经过2小时维修，于12点30分完成修复。"
} | ConvertTo-Json

Write-Host "发送维修记录文本..."
$test8Response = Invoke-RestMethod -Uri "$computationUrl/maintenance/extract-time" -Method POST -ContentType "application/json" -Body $maintenanceData
Write-Host "响应: $($test8Response | ConvertTo-Json -Depth 2)"

if ($test8Response.calculatedDurationMinutes) {
    Write-Host "✅ 维修时间抽取成功 - 时长: $($test8Response.calculatedDurationMinutes)分钟" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "所有测试用例执行完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
