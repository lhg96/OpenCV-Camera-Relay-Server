#!/bin/bash

# Video Client Demo 실행 스크립트

echo "========================================="
echo "   Video Client Demo Starting..."
echo "========================================="

# macOS 카메라 권한 우회 설정
export OPENCV_AVFOUNDATION_SKIP_AUTH=1

# 클래스패스 설정 및 직접 Java 실행
echo "Compiling project..."
mvn compile -q

echo "Starting Video Client Demo..."
# 클래스패스에 target/classes와 모든 의존성 포함
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     -Djava.awt.headless=false \
     -DOPENCV_AVFOUNDATION_SKIP_AUTH=1 \
     com.multic.client.VideoClientDemo "$@"