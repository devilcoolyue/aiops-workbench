#!/bin/bash
#===================================================================
# aiops-workbench 停止脚本
#===================================================================

BIN_DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME=$(cd "$BIN_DIR/.." && pwd)
APP_NAME="aiops-workbench"
PID_FILE="$APP_HOME/bin/${APP_NAME}.pid"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ ! -f "$PID_FILE" ]; then
    echo -e "${YELLOW}[WARN] PID文件不存在，${APP_NAME} 可能未在运行${NC}"
    exit 0
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
    echo -e "${YELLOW}[WARN] 进程 $PID 不存在，清理PID文件${NC}"
    rm -f "$PID_FILE"
    exit 0
fi

echo -e "${GREEN}[INFO] 正在停止 ${APP_NAME} (PID: $PID)...${NC}"

# 优雅停止，等待最多30秒
kill "$PID"
TIMEOUT=30
while [ $TIMEOUT -gt 0 ]; do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo -e "${GREEN}[OK] ${APP_NAME} 已停止${NC}"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
    TIMEOUT=$((TIMEOUT - 1))
done

# 超时强制停止
echo -e "${YELLOW}[WARN] 优雅停止超时，强制终止...${NC}"
kill -9 "$PID" 2>/dev/null
rm -f "$PID_FILE"
echo -e "${GREEN}[OK] ${APP_NAME} 已强制停止${NC}"
