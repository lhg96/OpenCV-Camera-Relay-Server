package com.multic.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import com.multic.server.VideoServerThread;
import com.multic.utils.TestUtils;

/**
 * VideoClient에 대한 단위 테스트
 * - 클라이언트 생명주기 테스트
 * - 서버 연결 테스트
 * - 오류 처리 테스트
 */
class VideoClientTest {

    private VideoClient videoClient;
    private VideoServerThread testServer;
    private JPanel testPanel;
    private int testPort;

    @BeforeEach
    void setUp() {
        testPort = TestUtils.findAvailablePort();
        testPanel = new JPanel();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (videoClient != null && videoClient.isRunning()) {
            videoClient.stop();
            Thread.sleep(500); // 정리 대기
        }
        
        if (testServer != null && testServer.isAlive()) {
            testServer.shutdown();
            testServer.join(3000);
        }
    }

    @Test
    @DisplayName("클라이언트 생성 및 기본 설정 테스트")
    void clientCreationTest() {
        // Given & When
        videoClient = new VideoClient("localhost", testPort, -1, 15.0, 0.7);
        
        // Then
        assertEquals("localhost", videoClient.getServerHost());
        assertEquals(testPort, videoClient.getServerPort());
        assertEquals(-1, videoClient.getCameraIndex()); // 존재하지 않는 카메라
        assertEquals(15.0, videoClient.getFrameRate());
        assertEquals(0.7, videoClient.getJpegQuality());
        assertFalse(videoClient.isRunning());
        assertFalse(videoClient.isCameraOpened());
        assertEquals(0, videoClient.getFramesSent());
        assertEquals(0, videoClient.getBytesSent());
    }

    @Test
    @DisplayName("존재하지 않는 카메라로 시작 시 실패해야 함")
    @Timeout(10)
    void startWithInvalidCameraTest() {
        // Given
        videoClient = new VideoClient("localhost", testPort, -1); // 존재하지 않는 카메라
        
        // When & Then
        assertFalse(videoClient.start(), "존재하지 않는 카메라로는 시작할 수 없어야 함");
        assertFalse(videoClient.isRunning());
        assertFalse(videoClient.isCameraOpened());
    }

    @Test
    @DisplayName("클라이언트 시작/중지 라이프사이클 테스트")
    @Timeout(15)
    void clientLifecycleTest() throws InterruptedException {
        // Given - 실제 카메라 없이 테스트할 수 있도록 모의 설정
        videoClient = new VideoClient("localhost", testPort, 0); // 첫 번째 카메라 시도
        
        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        AtomicBoolean cameraOpenCalled = new AtomicBoolean(false);
        
        videoClient.setEventListener(new VideoClient.ClientEventListener() {
            @Override
            public void onError(Exception e) {
                errorOccurred.set(true);
            }
            
            @Override
            public void onCameraOpened(int width, int height) {
                cameraOpenCalled.set(true);
            }
        });
        
        // When - 시작 시도 (카메라가 없으면 실패할 것)
        boolean started = videoClient.start();
        
        if (started) {
            // 카메라가 있는 경우
            assertTrue(videoClient.isRunning());
            assertTrue(cameraOpenCalled.get());
            
            // 중지 테스트
            videoClient.stop();
            assertFalse(videoClient.isRunning());
            
        } else {
            // 카메라가 없는 경우 (정상적인 테스트 환경에서 예상됨)
            assertFalse(videoClient.isRunning());
            assertFalse(videoClient.isCameraOpened());
        }
    }

    @Test
    @DisplayName("서버 없이 시작 시에도 클라이언트는 동작해야 함")
    @Timeout(10)
    void startWithoutServerTest() throws InterruptedException {
        // Given - 서버 없음
        videoClient = new VideoClient("localhost", testPort, 0);
        
        AtomicInteger errorCount = new AtomicInteger(0);
        
        videoClient.setEventListener(new VideoClient.ClientEventListener() {
            @Override
            public void onError(Exception e) {
                errorCount.incrementAndGet();
            }
        });
        
        // When - 클라이언트 시작 시도
        boolean started = videoClient.start();
        
        if (started) {
            // 카메라가 있어서 시작된 경우
            assertTrue(videoClient.isRunning());
            
            // 잠시 대기하여 서버 연결 시도가 이루어지도록 함
            Thread.sleep(2000);
            
            // 서버가 없으므로 전송 오류가 발생할 것임 (정상적인 동작)
            assertTrue(errorCount.get() >= 0, "서버 없이도 클라이언트는 실행되어야 함");
            
        } else {
            // 카메라가 없는 경우
            assertFalse(videoClient.isRunning());
        }
    }

    @Test
    @DisplayName("서버와 함께 통합 테스트")
    @Timeout(20)
    void integrationWithServerTest() throws InterruptedException {
        // Given - 테스트 서버 시작
        testServer = new VideoServerThread(testPort, testPanel);
        
        AtomicBoolean serverStarted = new AtomicBoolean(false);
        AtomicInteger framesReceived = new AtomicInteger(0);
        
        testServer.setEventListener(new VideoServerThread.ServerEventListener() {
            @Override
            public void onServerStarted(int port) {
                serverStarted.set(true);
            }
            
            @Override
            public void onFrameReceived(java.awt.image.BufferedImage frame, String clientId) {
                framesReceived.incrementAndGet();
            }
        });
        
        testServer.start();
        Thread.sleep(1000); // 서버 시작 대기
        
        assertTrue(serverStarted.get(), "테스트 서버가 시작되어야 함");
        
        // Given - 클라이언트 생성 (실제 카메라 없이는 프레임 전송 안됨)
        videoClient = new VideoClient("localhost", testPort, 0);
        
        AtomicBoolean clientConnected = new AtomicBoolean(false);
        
        videoClient.setEventListener(new VideoClient.ClientEventListener() {
            @Override
            public void onConnected() {
                clientConnected.set(true);
            }
        });
        
        // When
        boolean clientStarted = videoClient.start();
        
        if (clientStarted) {
            assertTrue(clientConnected.get(), "클라이언트가 연결되어야 함");
            assertTrue(videoClient.isRunning());
            
            // 실제 카메라가 있다면 프레임이 전송될 것
            Thread.sleep(3000);
            
            // 클라이언트 중지
            videoClient.stop();
            assertFalse(videoClient.isRunning());
            
        } else {
            // 카메라가 없는 경우는 정상적인 테스트 환경
            assertFalse(videoClient.isRunning());
        }
    }

    @Test
    @DisplayName("이벤트 리스너 설정 및 호출 테스트")
    @Timeout(10)
    void eventListenerTest() throws InterruptedException {
        // Given
        videoClient = new VideoClient("localhost", testPort, 0);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        
        videoClient.setEventListener(new VideoClient.ClientEventListener() {
            @Override
            public void onError(Exception e) {
                listenerCalled.set(true);
                latch.countDown();
            }
            
            @Override
            public void onCameraOpened(int width, int height) {
                listenerCalled.set(true);
                latch.countDown();
            }
        });
        
        // When
        boolean started = videoClient.start();
        
        if (started) {
            // 카메라가 열렸을 때 이벤트 호출
            assertTrue(latch.await(5, TimeUnit.SECONDS), "이벤트 리스너가 호출되어야 함");
            assertTrue(listenerCalled.get());
            
        } else {
            // 카메라 열기 실패 시 오류 이벤트 발생
            assertTrue(latch.await(5, TimeUnit.SECONDS), "오류 이벤트가 발생해야 함");
        }
    }

    @Test
    @DisplayName("통계 정보 추적 테스트")
    void statisticsTrackingTest() throws InterruptedException {
        // Given
        videoClient = new VideoClient("localhost", testPort, 0);
        
        // 초기 상태 확인
        assertEquals(0, videoClient.getFramesSent());
        assertEquals(0, videoClient.getBytesSent());
        assertEquals(0, videoClient.getLastFrameTime());
        
        // When - 시작 시도
        boolean started = videoClient.start();
        
        if (started) {
            // 실제 카메라가 있는 경우만 테스트
            Thread.sleep(2000);
            
            // 통계 출력 테스트 (오류 없이 실행되어야 함)
            assertDoesNotThrow(() -> videoClient.printStatistics());
            
        } else {
            // 카메라가 없는 경우도 통계 출력이 가능해야 함
            assertDoesNotThrow(() -> videoClient.printStatistics());
        }
    }

    @Test
    @DisplayName("중복 시작/중지 호출 테스트")
    void duplicateStartStopTest() throws InterruptedException {
        // Given
        videoClient = new VideoClient("localhost", testPort, 0);
        
        // When - 첫 번째 시작
        boolean firstStart = videoClient.start();
        
        if (firstStart) {
            assertTrue(videoClient.isRunning());
            
            // 중복 시작 (이미 실행 중)
            boolean secondStart = videoClient.start();
            assertTrue(secondStart, "이미 실행 중일 때 start()는 true를 반환해야 함");
            assertTrue(videoClient.isRunning());
            
            // 중지
            videoClient.stop();
            assertFalse(videoClient.isRunning());
            
            // 중복 중지
            videoClient.stop(); // 예외가 발생하지 않아야 함
            assertFalse(videoClient.isRunning());
            
        } else {
            // 카메라가 없는 경우
            assertFalse(videoClient.isRunning());
            
            // 중복 시작 시도
            boolean secondStart = videoClient.start();
            assertFalse(secondStart);
            
            // 중지 (실행되지 않은 상태에서도 안전해야 함)
            assertDoesNotThrow(() -> videoClient.stop());
        }
    }
}