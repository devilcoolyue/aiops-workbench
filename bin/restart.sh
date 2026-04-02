#!/bin/bash
#===================================================================
# aiops-workbench 重启脚本
#===================================================================

BIN_DIR=$(cd "$(dirname "$0")" && pwd)

echo "[INFO] 停止服务..."
bash "$BIN_DIR/stop.sh"

sleep 2

echo "[INFO] 启动服务..."
bash "$BIN_DIR/start.sh"
