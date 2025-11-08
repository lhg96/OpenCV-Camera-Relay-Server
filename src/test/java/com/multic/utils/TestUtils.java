package com.multic.utils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

/**
 * 테스트용 유틸리티 클래스
 */
public class TestUtils {
    
    private static final Random random = new Random();
    
    /**
     * 사용 가능한 포트를 찾아 반환
     * @return 사용 가능한 포트 번호, 찾지 못하면 -1
     */
    public static int findAvailablePort() {
        return findAvailablePort(8000, 9000);
    }
    
    /**
     * 지정된 범위에서 사용 가능한 포트를 찾아 반환
     * @param min 최소 포트 번호
     * @param max 최대 포트 번호
     * @return 사용 가능한 포트 번호, 찾지 못하면 -1
     */
    public static int findAvailablePort(int min, int max) {
        for (int i = 0; i < 100; i++) { // 최대 100번 시도
            int port = min + random.nextInt(max - min);
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }
    
    /**
     * 포트가 사용 가능한지 확인
     * @param port 확인할 포트 번호
     * @return 사용 가능하면 true, 아니면 false
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 지정된 시간만큼 대기
     * @param millis 대기할 시간 (밀리초)
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 테스트용 임시 데이터 생성
     * @param size 생성할 데이터 크기
     * @return 임시 바이트 배열
     */
    public static byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }
}