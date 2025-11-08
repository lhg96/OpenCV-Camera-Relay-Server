#!/bin/bash

# OpenCV 관련 환경 변수 설정
export OPENCV_AVFOUNDATION_SKIP_AUTH=1

# 서버 실행
echo "Starting Multics Camera Relay Server..."
echo "Environment variable OPENCV_AVFOUNDATION_SKIP_AUTH=$OPENCV_AVFOUNDATION_SKIP_AUTH"

# 포트 인자가 있으면 전달, 없으면 기본 포트 사용
if [ $# -eq 0 ]; then
    echo "Using default port 5252"
    mvn exec:java
else
    echo "Using port $1"
    mvn exec:java -Dexec.args="$1"
fi

echo "Server finished."