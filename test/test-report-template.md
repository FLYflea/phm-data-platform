# PHM-Platform 端到端测试报告

## 测试概述
- **测试日期**: 2026-03-05
- **测试环境**: Windows 22H2, Java 17, Docker Desktop
- **服务版本**: 1.0.0-SNAPSHOT

## 服务状态检查

| 服务 | 端口 | 状态 | 健康检查 |
|------|------|------|----------|
| Gateway | 8080 | ✅ 运行中 | /actuator/health |
| Collection | 8101 | ✅ 运行中 | /health |
| Computation | 8102 | ✅ 运行中 | /actuator/health |
| Storage | 8103 | ✅ 运行中 | /actuator/health |
| Service | 8104 | ✅ 运行中 | /actuator/health |
| TimescaleDB | 5432 | ✅ 运行中 | healthy |
| Neo4j | 7474 | ✅ 运行中 | healthy |
| pgAdmin | 8081 | ✅ 运行中 | - |

## API 测试结果

### 测试用例 1: 在线数据采集 → 计算同步 → 存储 → 查询
```bash
# 请求
curl -X POST http://localhost:8080/api/collection/sensor \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "TEST_DEVICE_001",
    "sensorType": "temperature",
    "value": 25.5,
    "timestamp": "2026-03-05T10:30:00"
  }'

# 预期响应
{
  "status": "success",
  "data": {
    "deviceId": "TEST_DEVICE_001",
    "savedCount": 1,
    "processingTimeMs": 150
  },
  "note": "数据已接收并转发到计算层处理"
}
```

**结果**: ⏳ 待执行
**截图**: 截图 1 - 数据录入界面

---

### 测试用例 2: 批量采集 + Delta 压缩比计算
```bash
# 请求
curl -X POST http://localhost:8080/api/collection/sensor/batch \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "TEST_DEVICE_002",
    "sensorType": "vibration",
    "data": [10.5, 10.8, 11.2, 10.9, 11.5, 11.0, 10.7, 11.3, 10.6, 11.1]
  }'

# 预期响应
{
  "status": "success",
  "data": {
    "savedCount": 10,
    "compressionRatio": 2.35,
    "originalSize": 80,
    "compressedSize": 34
  }
}
```

**结果**: ⏳ 待执行
**性能指标**: 
- 处理时间: ___ ms
- 压缩比: ___ 

---

### 测试用例 3: 时间同步算法验证（不确定性同步）
```bash
# 请求
curl -X POST http://localhost:8080/api/computation/sync/uncertainty \
  -H "Content-Type: application/json" \
  -d '{
    "rawData": [
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 25.0, "timestamp": "2026-03-05T10:00:00"},
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 25.5, "timestamp": "2026-03-05T10:00:01"}
    ],
    "kNeighbors": 2,
    "expectedIntervalMs": 1000
  }'

# 预期响应
{
  "status": "success",
  "data": {
    "syncedData": [...],
    "averageConfidence": 0.95,
    "interpolatedCount": 0,
    "processingTimeMs": 45
  }
}
```

**结果**: ⏳ 待执行
**算法指标**:
- 平均置信度: ___
- 插值修复数: ___

---

### 测试用例 4: PDA 数据融合验证
```bash
# 请求
curl -X POST http://localhost:8080/api/computation/fusion/pda \
  -H "Content-Type: application/json" \
  -d '{
    "multiSourceData": [
      [{"deviceId": "DEV_001", "value": 100.2, "timestamp": "2026-03-05T10:00:00"}],
      [{"deviceId": "DEV_001", "value": 100.3, "timestamp": "2026-03-05T10:00:00"}]
    ]
  }'

# 预期响应
{
  "status": "success",
  "data": {
    "fusionResults": [...],
    "varianceReduction": "35%"
  }
}
```

**结果**: ⏳ 待执行
**算法指标**:
- 融合精度: ___
- 方差缩减: ___%

---

### 测试用例 5: 知识图谱构建 → 查询
```bash
# 构建请求
curl -X POST http://localhost:8080/api/computation/knowledge/build/text \
  -H "Content-Type: application/json" \
  -d '{
    "docId": "MAINTENANCE_DOC_001",
    "text": "设备EQ001的主轴承发生故障...",
    "equipmentId": "EQ001"
  }'

# 查询请求
curl -X GET http://localhost:8080/api/storage/graph/equipment/EQ001
```

**结果**: ⏳ 待执行
**截图**: 截图 11 - 知识图谱可视化

---

### 测试用例 6: 故障模式存储 → FMECA 查询
```bash
# 保存请求
curl -X POST http://localhost:8080/api/storage/fmeca/failure-mode \
  -H "Content-Type: application/json" \
  -d '{
    "failureModeId": "FM_001",
    "equipmentId": "EQ001",
    "name": "主轴承磨损",
    "severityClass": 4,
    "occurrenceLevel": "B",
    "detectionLevel": 3
  }'

# 预期RPN = 4 × 4 × 3 = 48
```

**结果**: ⏳ 待执行
**RPN计算**: 预期 48

---

### 测试用例 7: 特征工程验证
```bash
# 请求
curl -X POST http://localhost:8080/api/computation/feature/time-domain \
  -H "Content-Type: application/json" \
  -d '{"values": [10.5, 20.3, 15.8, 25.1, 18.7, 22.4, 19.6, 21.3]}'

# 预期响应
{
  "status": "success",
  "data": {
    "mean": 19.21,
    "stdDev": 4.52,
    "rms": 19.73,
    "peak": 25.1,
    "max": 25.1,
    "min": 10.5
  }
}
```

**结果**: ⏳ 待执行
**截图**: 截图 10 - 特征提取图表

---

### 测试用例 8: 维修时间抽取
```bash
# 请求
curl -X POST http://localhost:8080/api/computation/maintenance/extract-time \
  -H "Content-Type: application/json" \
  -d '{
    "maintenanceText": "2026年3月5日10点30分开始维修...经过2小时维修，于12点30分完成修复。"
  }'

# 预期响应
{
  "status": "success",
  "startTime": "2026-03-05T10:30:00",
  "endTime": "2026-03-05T12:30:00",
  "calculatedDurationMinutes": 120
}
```

**结果**: ⏳ 待执行

---

## 数据库验证

### TimescaleDB (pgAdmin)
- **连接**: localhost:8081
- **账号**: admin@phm.com / admin
- **SQL验证**: 
  ```sql
  SELECT COUNT(*) FROM sensor_timeseries;
  SELECT * FROM sensor_timeseries LIMIT 5;
  ```

### Neo4j Browser
- **连接**: http://localhost:7474
- **账号**: neo4j / phm123456
- **Cypher验证**:
  ```cypher
  MATCH (n) RETURN count(n);
  MATCH (e:Equipment)-[:HAS_COMPONENT]->(c:Component) RETURN e, c LIMIT 5;
  ```

---

## 性能测试结果

| 测试项 | 目标 | 实际 | 状态 |
|--------|------|------|------|
| 单条数据采集 | < 500ms | ___ ms | ⏳ |
| 批量处理(100条) | < 2s | ___ ms | ⏳ |
| 时间同步(1000点) | < 1s | ___ ms | ⏳ |
| 数据融合 | < 500ms | ___ ms | ⏳ |
| 特征提取(1000点) | < 1s | ___ ms | ⏳ |
| 页面加载 | < 3s | ___ ms | ⏳ |

---

## 测试结论

- **通过测试**: ___ / 8
- **失败测试**: ___ / 8
- **整体状态**: ⏳ 待完成

## 截图清单（用于论文）

1. 系统架构图
2. 服务状态监控面板
3. 数据录入界面
4. 批量导入结果
5. 时间同步算法结果
6. PDA融合结果
7. 知识图谱可视化
8. 特征提取图表
9. FMECA故障模式管理
10. 维修时间抽取结果
11. 数据库查询界面
12. 前端页面整体布局

---

**测试人员**: _____________
**审核人员**: _____________
**日期**: 2026-03-05
