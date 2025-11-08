package com.multic.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.multic.server.CameraServer;

/**
 * CameraServer 클래스에 대한 단위 테스트
 * - 카메라 초기화 및 생명주기 테스트
 * - 설정 변경 테스트  
 * - 스냅샷 기능 테스트
 * - 이벤트 콜백 테스트
 */
class CameraServerTest {

    private CameraServer cameraServer;
    private final AtomicBoolean cameraStarted = new AtomicBoolean(false);
    private final AtomicBoolean cameraStopped = new AtomicBoolean(false);
    private final AtomicBoolean errorOccurred = new AtomicBoolean(false);
    private final AtomicInteger frameCaptureCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        // 가상 카메라 인덱스 사용 (실제 카메라가 없어도 테스트 가능)
        cameraServer = new CameraServer(-1, 10, 0.5); // 존재하지 않는 카메라 인덱스
        setupEventListener();
        resetTestFlags();
    }

    @AfterEach
    void tearDown() {
        if (cameraServer != null) {
            cameraServer.shutdown();
        }
    }

    private void setupEventListener() {
        cameraServer.setEventListener(new CameraServer.CameraEventListener() {
            @Override
            public void onCameraStarted() {
                cameraStarted.set(true);
            }

            @Override
            public void onCameraStopped() {
                cameraStopped.set(true);
            }

            @Override
            public void onFrameCaptured(BufferedImage frame) {
                frameCaptureCount.incrementAndGet();
            }

            @Override
            public void onError(Exception e) {
                errorOccurred.set(true);
            }
        });
    }

    private void resetTestFlags() {
        cameraStarted.set(false);
        cameraStopped.set(false);
        errorOccurred.set(false);
        frameCaptureCount.set(0);
    }

    @Test
    @DisplayName("카메라 서버 초기화가 정상적으로 이루어져야 함")
    void cameraServerInitializationTest() {
        // Given & When & Then
        assertNotNull(cameraServer, "카메라 서버가 정상적으로 생성되어야 함");
        assertFalse(cameraServer.isCameraRunning(), "초기 상태에서 카메라가 실행중이면 안됨");
        assertFalse(cameraServer.isStreamingEnabled(), "초기 상태에서 스트리밍이 활성화되면 안됨");
        assertEquals(0, cameraServer.getCurrentFrameNumber(), "초기 프레임 번호는 0이어야 함");
    }

    @Test
    @DisplayName("카메라 설정이 정확하게 적용되어야 함")
    void cameraConfigurationTest() {
        // Given
        int expectedCameraIndex = 1;
        int expectedFrameRate = 15;
        double expectedQuality = 0.7;
        
        // When
        cameraServer.setCameraIndex(expectedCameraIndex);
        cameraServer.setFrameRate(expectedFrameRate);
        cameraServer.setJpegQuality(expectedQuality);
        
        // Then
        assertEquals(expectedCameraIndex, cameraServer.getCameraIndex(), 
            "카메라 인덱스가 정확히 설정되어야 함");
        assertEquals(expectedFrameRate, cameraServer.getFrameRate(), 
            "프레임 레이트가 정확히 설정되어야 함");
        assertEquals(expectedQuality, cameraServer.getJpegQuality(), 0.001, 
            "JPEG 품질이 정확히 설정되어야 함");
    }

    @Test
    @DisplayName("서버 주소 설정이 정상적으로 적용되어야 함")
    void serverAddressConfigurationTest() {
        // Given
        String expectedHost = "192.168.1.100";
        int expectedPort = 8080;
        
        // When
        cameraServer.setServerAddress(expectedHost, expectedPort);
        
        // Then
        assertEquals(expectedHost, cameraServer.getServerHost(), 
            "서버 호스트가 정확히 설정되어야 함");
        assertEquals(expectedPort, cameraServer.getServerPort(), 
            "서버 포트가 정확히 설정되어야 함");
    }

    @Test
    @DisplayName("프레임 레이트 범위 검증이 이루어져야 함")
    void frameRateValidationTest() {
        // Given & When & Then
        cameraServer.setFrameRate(0); // 최소값 이하
        assertTrue(cameraServer.getFrameRate() >= 1, "프레임 레이트는 최소 1이어야 함");
        
        cameraServer.setFrameRate(100); // 최대값 초과
        assertTrue(cameraServer.getFrameRate() <= 60, "프레임 레이트는 최대 60이어야 함");
        
        cameraServer.setFrameRate(30); // 정상 범위
        assertEquals(30, cameraServer.getFrameRate(), "정상 범위의 프레임 레이트가 설정되어야 함");
    }

    @Test
    @DisplayName("JPEG 품질 범위 검증이 이루어져야 함")
    void jpegQualityValidationTest() {
        // Given & When & Then
        cameraServer.setJpegQuality(0.0); // 최소값 이하
        assertTrue(cameraServer.getJpegQuality() >= 0.1, "JPEG 품질은 최소 0.1이어야 함");
        
        cameraServer.setJpegQuality(1.5); // 최대값 초과
        assertTrue(cameraServer.getJpegQuality() <= 1.0, "JPEG 품질은 최대 1.0이어야 함");
        
        cameraServer.setJpegQuality(0.8); // 정상 범위
        assertEquals(0.8, cameraServer.getJpegQuality(), 0.001, 
            "정상 범위의 JPEG 품질이 설정되어야 함");
    }

    @Test
    @DisplayName("존재하지 않는 카메라 시작 시 오류가 발생해야 함")
    @Timeout(5)
    void startNonExistentCameraTest() throws InterruptedException {
        // Given
        assumeTrue(cameraServer.getCameraIndex() == -1, "존재하지 않는 카메라 인덱스여야 함");
        
        // When
        boolean result = cameraServer.startCamera();
        Thread.sleep(100); // 이벤트 처리 대기
        
        // Then
        assertFalse(result, "존재하지 않는 카메라 시작이 실패해야 함");
        assertFalse(cameraServer.isCameraRunning(), "카메라가 실행 중이면 안됨");
    }

    @Test
    @DisplayName("카메라가 실행되지 않은 상태에서 스냅샷 시도 시 null 반환")
    void snapshotWithoutCameraTest() {
        // Given
        assertFalse(cameraServer.isCameraRunning(), "카메라가 실행중이면 안됨");
        
        // When
        BufferedImage snapshot = cameraServer.captureSnapshot();
        
        // Then
        assertNull(snapshot, "카메라가 실행되지 않은 상태에서 스냅샷은 null이어야 함");
    }

    @Test
    @DisplayName("스트리밍 상태 관리가 정상적으로 이루어져야 함")
    void streamingStateManagementTest() {
        // Given
        assertFalse(cameraServer.isStreamingEnabled(), "초기에는 스트리밍이 비활성화되어야 함");
        
        // When
        cameraServer.startStreaming();
        
        // Then
        assertTrue(cameraServer.isStreamingEnabled(), "스트리밍 시작 후 활성화되어야 함");
        
        // When
        cameraServer.stopStreaming();
        
        // Then
        assertFalse(cameraServer.isStreamingEnabled(), "스트리밍 중지 후 비활성화되어야 함");
    }

    @Test
    @DisplayName("카메라 서버 종료 시 모든 리소스가 정리되어야 함")
    @Timeout(5)
    void shutdownResourceCleanupTest() throws InterruptedException {
        // Given
        cameraServer.startStreaming();
        assertTrue(cameraServer.isStreamingEnabled(), "스트리밍이 활성화되어야 함");
        
        // When
        cameraServer.shutdown();
        Thread.sleep(500); // 종료 처리 대기
        
        // Then
        assertFalse(cameraServer.isStreamingEnabled(), "종료 후 스트리밍이 비활성화되어야 함");
        assertFalse(cameraServer.isCameraRunning(), "종료 후 카메라가 중지되어야 함");
    }

    @Test
    @DisplayName("카메라 이벤트 리스너가 정상적으로 동작해야 함")
    void eventListenerTest() throws InterruptedException {
        // Given
        resetTestFlags();
        
        // When - 카메라 시작 시도 (실패할 것임)
        cameraServer.startCamera();
        Thread.sleep(200);
        
        // When - 카메라 중지
        cameraServer.stopCamera();
        Thread.sleep(200);
        
        // Then
        // 존재하지 않는 카메라이므로 오류 또는 중지 이벤트가 발생해야 함
        assertTrue(cameraStopped.get() || errorOccurred.get(), 
            "카메라 중지 또는 오류 이벤트가 발생해야 함");
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 스레드에서 설정 변경")
    @Timeout(10)
    void concurrentConfigurationTest() throws InterruptedException {
        // Given
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When - 여러 스레드에서 동시에 설정 변경
        for (int i = 0; i < threadCount; i++) {
            final int value = i + 10;
            new Thread(() -> {
                try {
                    cameraServer.setFrameRate(value);
                    cameraServer.setJpegQuality(0.5 + (value * 0.1));
                    cameraServer.setCameraIndex(value);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "모든 스레드가 완료되어야 함");
        
        // 최종 설정값들이 유효한 범위 내에 있어야 함
        assertTrue(cameraServer.getFrameRate() >= 1 && cameraServer.getFrameRate() <= 60,
            "프레임 레이트가 유효한 범위에 있어야 함");
        assertTrue(cameraServer.getJpegQuality() >= 0.1 && cameraServer.getJpegQuality() <= 1.0,
            "JPEG 품질이 유효한 범위에 있어야 함");
    }

    @Test
    @DisplayName("파일 저장 경로가 유효하지 않을 때 적절히 처리해야 함")
    void invalidFilePathTest() {
        // Given
        String invalidPath = "/invalid/path/that/does/not/exist/snapshot.jpg";
        
        // When
        boolean result = cameraServer.saveSnapshot(invalidPath);
        
        // Then
        assertFalse(result, "유효하지 않은 경로로의 저장이 실패해야 함");
    }
}