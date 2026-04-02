#!/bin/bash
#===================================================================
# aiops-workbench 启动脚本
#===================================================================

# 获取脚本所在目录，推导应用根目录
BIN_DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME=$(cd "$BIN_DIR/.." && pwd)
APP_NAME="aiops-workbench"
JAR_FILE=$(ls "$APP_HOME"/${APP_NAME}*.jar 2>/dev/null | head -1)
PID_FILE="$APP_HOME/bin/${APP_NAME}.pid"
LOG_DIR="$APP_HOME/logs"

# JVM参数（可根据实际情况调整）
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

# Spring配置文件位置
SPRING_OPTS="--spring.config.location=file:${APP_HOME}/config/"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}[ERROR] 未找到jar文件，请先执行 mvn clean package${NC}"
    exit 1
fi

# 创建日志目录
mkdir -p "$LOG_DIR"

# 检查是否已在运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo -e "${YELLOW}[WARN] ${APP_NAME} 已在运行 (PID: $PID)${NC}"
        exit 1
    else
        rm -f "$PID_FILE"
    fi
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} Starting ${APP_NAME}${NC}"
echo -e "${GREEN} JAR: $(basename $JAR_FILE)${NC}"
echo -e "${GREEN} JAVA_OPTS: ${JAVA_OPTS}${NC}"
echo -e "${GREEN}========================================${NC}"

# 启动应用
nohup java $JAVA_OPTS -jar "$JAR_FILE" $SPRING_OPTS > "$LOG_DIR/console.log" 2>&1 &
PID=$!
echo $PID > "$PID_FILE"

# 等待几秒检查进程是否正常启动
sleep 3
if kill -0 "$PID" 2>/dev/null; then
    echo -e "${GREEN}[OK] ${APP_NAME} 启动成功 (PID: $PID)${NC}"
    echo -e "${GREEN}[OK] 日志文件: ${LOG_DIR}/console.log${NC}"
else
    echo -e "${RED}[ERROR] ${APP_NAME} 启动失败，请查看日志: ${LOG_DIR}/console.log${NC}"
    rm -f "$PID_FILE"
    exit 1
fi
