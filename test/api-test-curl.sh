#!/bin/bash
# PHM-Platform 端到端测试脚本 (curl 版本)
# 适用于 Linux/Mac/Git Bash

BASE_URL="http://localhost:8080"
COLLECTION_URL="$BASE_URL/api/collection"
COMPUTATION_URL="$BASE_URL/api/computation"
STORAGE_URL="$BASE_URL/api/storage"
SERVICE_URL="$BASE_URL/api/service"

echo "========================================"
echo "PHM-Platform 端到端 API 测试 (curl)"
echo "========================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ==================== 测试用例 1：在线采集 → 计算同步 → 存储 → 查询 ====================
echo -e "${YELLOW}【测试用例 1】在线采集 → 计算同步 → 存储 → 查询${NC}"
echo "----------------------------------------"

echo "Step 1: 发送传感器数据到采集层..."
curl -s -X POST "$COLLECTION_URL/sensor" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "TEST_DEVICE_001",
    "sensorType": "temperature",
    "value": 25.5,
    "timestamp": "2026-03-05T10:30:00"
  }' | jq .

echo ""
echo "等待数据处理..."
sleep 2

echo "Step 2: 查询存储的数据..."
curl -s -X POST "$SERVICE_URL/query/raw" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "TEST_DEVICE_001",
    "startTime": "2026-03-05T00:00:00Z",
    "endTime": "2026-03-05T23:59:59Z"
  }' | jq .

echo -e "${GREEN}✅ 测试用例 1 完成${NC}"
echo ""

# ==================== 测试用例 2：批量采集 + Delta 压缩比计算 ====================
echo -e "${YELLOW}【测试用例 2】批量采集 + Delta 压缩比计算${NC}"
echo "----------------------------------------"

curl -s -X POST "$COLLECTION_URL/sensor/batch" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "TEST_DEVICE_002",
    "sensorType": "vibration",
    "data": [10.5, 10.8, 11.2, 10.9, 11.5, 11.0, 10.7, 11.3, 10.6, 11.1]
  }' | jq .

echo -e "${GREEN}✅ 测试用例 2 完成${NC}"
echo ""

# ==================== 测试用例 3：时间同步算法验证 ====================
echo -e "${YELLOW}【测试用例 3】时间同步算法验证（不确定性同步）${NC}"
echo "----------------------------------------"

curl -s -X POST "$COMPUTATION_URL/sync/uncertainty" \
  -H "Content-Type: application/json" \
  -d '{
    "rawData": [
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 25.0, "timestamp": "2026-03-05T10:00:00"},
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 25.5, "timestamp": "2026-03-05T10:00:01"},
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 26.0, "timestamp": "2026-03-05T10:00:03"},
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 26.5, "timestamp": "2026-03-05T10:00:04"},
      {"deviceId": "DEV_001", "sensorType": "temp", "value": 27.0, "timestamp": "2026-03-05T10:00:06"}
    ],
    "kNeighbors": 2,
    "expectedIntervalMs": 1000
  }' | jq .

echo -e "${GREEN}✅ 测试用例 3 完成${NC}"
echo ""

# ==================== 测试用例 4：PDA 数据融合验证 ====================
echo -e "${YELLOW}【测试用例 4】PDA 数据融合验证${NC}"
echo "----------------------------------------"

curl -s -X POST "$COMPUTATION_URL/fusion/pda" \
  -H "Content-Type: application/json" \
  -d '{
    "multiSourceData": [
      [
        {"deviceId": "DEV_001", "sensorType": "pressure", "value": 100.2, "timestamp": "2026-03-05T10:00:00"},
        {"deviceId": "DEV_001", "sensorType": "pressure", "value": 100.5, "timestamp": "2026-03-05T10:00:01"}
      ],
      [
        {"deviceId": "DEV_001", "sensorType": "pressure", "value": 100.3, "timestamp": "2026-03-05T10:00:00"},
        {"deviceId": "DEV_001", "sensorType": "pressure", "value": 100.4, "timestamp": "2026-03-05T10:00:01"}
      ]
    ]
  }' | jq .

echo -e "${GREEN}✅ 测试用例 4 完成${NC}"
echo ""

# ==================== 测试用例 5：知识图谱构建 → 查询 ====================
echo -e "${YELLOW}【测试用例 5】知识图谱构建 → 查询${NC}"
echo "----------------------------------------"

echo "Step 1: 从文本构建知识图谱..."
curl -s -X POST "$COMPUTATION_URL/knowledge/build/text" \
  -H "Content-Type: application/json" \
  -d '{
    "docId": "MAINTENANCE_DOC_001",
    "text": "设备EQ001的主轴承发生故障，导致温度传感器TS001读数异常。更换轴承后设备恢复正常。",
    "equipmentId": "EQ001"
  }' | jq .

echo ""
echo "等待数据处理..."
sleep 2

echo "Step 2: 查询知识图谱..."
curl -s -X GET "$STORAGE_URL/graph/equipment/EQ001" | jq .

echo -e "${GREEN}✅ 测试用例 5 完成${NC}"
echo ""

# ==================== 测试用例 6：故障模式存储 → FMECA 查询 ====================
echo -e "${YELLOW}【测试用例 6】故障模式存储 → FMECA 查询${NC}"
echo "----------------------------------------"

echo "Step 1: 保存故障模式..."
curl -s -X POST "$STORAGE_URL/fmeca/failure-mode" \
  -H "Content-Type: application/json" \
  -d '{
    "failureModeId": "FM_001",
    "equipmentId": "EQ001",
    "componentId": "BEARING_001",
    "name": "主轴承磨损",
    "description": "主轴承因润滑不足导致磨损",
    "causes": ["润滑不足", "负载过大"],
    "localEffect": "轴承温度升高",
    "finalEffect": "设备停机",
    "severityClass": 4,
    "occurrenceLevel": "B",
    "detectionLevel": 3,
    "improvementMeasures": ["定期润滑", "温度监测"],
    "sourceDocId": "DOC_001"
  }' | jq .

echo ""
echo "Step 2: 查询故障模式..."
curl -s -X GET "$STORAGE_URL/fmeca/failure-mode/equipment/EQ001" | jq .

echo -e "${GREEN}✅ 测试用例 6 完成${NC}"
echo ""

# ==================== 测试用例 7：特征工程验证 ====================
echo -e "${YELLOW}【测试用例 7】特征工程验证${NC}"
echo "----------------------------------------"

curl -s -X POST "$COMPUTATION_URL/feature/time-domain" \
  -H "Content-Type: application/json" \
  -d '{
    "values": [10.5, 20.3, 15.8, 25.1, 18.7, 22.4, 19.6, 21.3]
  }' | jq .

echo -e "${GREEN}✅ 测试用例 7 完成${NC}"
echo ""

# ==================== 测试用例 8：维修时间抽取 ====================
echo -e "${YELLOW}【测试用例 8】维修时间抽取${NC}"
echo "----------------------------------------"

curl -s -X POST "$COMPUTATION_URL/maintenance/extract-time" \
  -H "Content-Type: application/json" \
  -d '{
    "maintenanceText": "2026年3月5日10点30分开始维修，发现主轴承故障。经过2小时维修，于12点30分完成修复。"
  }' | jq .

echo -e "${GREEN}✅ 测试用例 8 完成${NC}"
echo ""

echo "========================================"
echo "所有测试用例执行完成！"
echo "========================================"
