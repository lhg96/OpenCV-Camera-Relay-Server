package com.multic.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import com.multic.server.CameraServer;
import com.multic.server.VideoServerThread;
import com.multic.server.config.ServerConfig;
import com.multic.utils.TestUtils;

/**
 * 전체 시스템 통합 테스트
 * - 카메라 서버와 비디오 서버 간의 통합 테스트
 * - 설정과 실제 동작의 일치성 테스트
 * - 전체 워크플로우 테스트
 */
class CameraServerIntegrationTest {

    private VideoServerThread videoServer;
    private CameraServer cameraServer;
    private ServerConfig config;
    private JPanel testPanel;
    private int testPort;
    
    // 테스트 상태 추적
    private final AtomicBoolean serverStarted = new AtomicBoolean(false);
    private final AtomicBoolean frameReceived = new AtomicBoolean(false);
    private final AtomicInteger clientConnections = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        // 테스트용 포트 찾기
        testPort = TestUtils.findAvailablePort();
        assumeTrue(testPort > 0, "사용 가능한 포트를 찾을 수 없음");
        
        // 컴포넌트 초기화
        config = new ServerConfig();
        config.setServerPort(testPort);
        config.setServerHost("localhost");
        config.setFrameRate(10); // 테스트용 낮은 프레임 레이트
        
        testPanel = new JPanel();
        
        resetTestFlags();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (cameraServer != null) {
            cameraServer.shutdown();
        }
        
        if (videoServer != null && videoServer.isAlive()) {
            videoServer.shutdown();
            videoServer.join(3000);
        }
    }
    
    private void resetTestFlags() {
        serverStarted.set(false);
        frameReceived.set(false);
        clientConnections.set(0);
    }

    @Test
    @DisplayName("비디오 서버와 카메라 서버의 전체 워크플로우 테스트")
    @Timeout(15)
    void fullWorkflowTest() throws InterruptedException {
        // Given - 비디오 서버 시작
        startVideoServer();
        assertTrue(serverStarted.get(), "비디오 서버가 시작되어야 함");
        
        // Given - 카메라 서버 설정 (실제 카메라 없이 테스트)
        cameraServer = new CameraServer(-1, config.getFrameRate(), config.getJpegQuality());
        cameraServer.setServerAddress(config.getServerHost(), config.getServerPort());
        
        // When - 테스트 프레임 전송 (카메라 대신)
        sendTestFramesToServer(3);
        
        // Then - 프레임 수신 확인
        Thread.sleep(2000); // 프레임 처리 대기
        assertTrue(videoServer.getTotalFramesReceived() > 0, 
            "비디오 서버가 프레임을 수신해야 함");
        assertTrue(frameReceived.get(), "프레임 수신 이벤트가 발생해야 함");
    }

    @Test
    @DisplayName("설정 변경이 실제 동작에 반영되는지 테스트")
    @Timeout(10)
    void configurationIntegrationTest() throws InterruptedException {
        // Given - 사용자 정의 설정
        int customPort = TestUtils.findAvailablePort();
        assumeTrue(customPort > 0, "사용 가능한 포트를 찾을 수 없음");
        
        config.setServerPort(customPort);
        config.setFrameRate(5);
        config.setJpegQuality(0.5);
        
        // When - 설정을 적용한 서버 시작
        startVideoServerWithPort(customPort);
        
        cameraServer = new CameraServer(-1, config.getFrameRate(), config.getJpegQuality());
        cameraServer.setServerAddress(config.getServerHost(), config.getServerPort());
        
        // Then - 설정이 올바르게 적용되었는지 확인
        assertEquals(customPort, config.getServerPort(), 
            "서버 포트가 설정대로 적용되어야 함");
        assertEquals(5, cameraServer.getFrameRate(),
            "프레임 레이트가 설정대로 적용되어야 함");
        assertEquals(0.5, cameraServer.getJpegQuality(), 0.001,
            "JPEG 품질이 설정대로 적용되어야 함");
    }

    @Test
    @DisplayName("동시 다중 클라이언트 연결 테스트")
    @Timeout(20)
    void multipleClientConnectionTest() throws InterruptedException {
        // Given
        startVideoServer();
        int clientCount = 5;
        CountDownLatch latch = new CountDownLatch(clientCount);
        
        // When - 여러 클라이언트가 동시에 프레임 전송
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            new Thread(() -> {
                try {
                    sendTestFramesToServer(2); // 각 클라이언트당 2개 프레임
                } finally {
                    latch.countDown();
                }
            }, "TestClient-" + clientId).start();
        }
        
        // Then
        assertTrue(latch.await(15, TimeUnit.SECONDS), 
            "모든 클라이언트가 완료되어야 함");
        
        Thread.sleep(2000); // 처리 대기
        
        assertTrue(videoServer.getTotalFramesReceived() >= clientCount,
            "최소 클라이언트 수만큼 프레임을 받아야 함");
        assertTrue(clientConnections.get() >= clientCount,
            "클라이언트 연결 이벤트가 발생해야 함");
    }

    @Test
    @DisplayName("서버 오류 상황에서의 복구 테스트")
    @Timeout(10)
    void errorRecoveryTest() throws InterruptedException {
        // Given - 서버가 시작되지 않은 상태
        cameraServer = new CameraServer(-1, config.getFrameRate(), config.getJpegQuality());
        cameraServer.setServerAddress(config.getServerHost(), testPort);
        
        // When - 서버 없이 스트리밍 시도 (실패할 것)
        cameraServer.startStreaming();
        Thread.sleep(1000);
        
        // Then - 오류 상태에서도 카메라 서버는 정상 동작해야 함
        assertTrue(cameraServer.isStreamingEnabled(), 
            "스트리밍은 활성화 상태를 유지해야 함");
        
        // When - 이후 서버 시작
        startVideoServer();
        Thread.sleep(1000);
        
        // When - 수동으로 프레임 전송 (카메라 없이)
        sendTestFramesToServer(1);
        Thread.sleep(1000);
        
        // Then - 서버 시작 후 프레임 수신 가능
        assertTrue(frameReceived.get(), 
            "서버 시작 후 프레임 수신이 가능해야 함");
    }

    @Test
    @DisplayName("리소스 정리 및 메모리 누수 방지 테스트")
    @Timeout(15)
    void resourceCleanupTest() throws InterruptedException {
        // Given
        startVideoServer();
        cameraServer = new CameraServer(-1, config.getFrameRate(), config.getJpegQuality());
        cameraServer.setServerAddress(config.getServerHost(), config.getServerPort());
        
        // When - 여러 차례 시작/중지 반복
        for (int i = 0; i < 3; i++) {
            cameraServer.startStreaming();
            sendTestFramesToServer(1);
            Thread.sleep(500);
            cameraServer.stopStreaming();
            Thread.sleep(500);
        }
        
        // When - 완전 종료
        cameraServer.shutdown();
        videoServer.shutdown();
        videoServer.join(5000);
        
        // Then - 리소스가 정리되어야 함
        assertFalse(videoServer.isAlive(), "비디오 서버 스레드가 종료되어야 함");
        assertFalse(cameraServer.isStreamingEnabled(), 
            "카메라 서버 스트리밍이 중지되어야 함");
        assertFalse(cameraServer.isCameraRunning(),
            "카메라가 중지되어야 함");
    }

    @Test
    @DisplayName("대용량 프레임 처리 성능 테스트")
    @Timeout(30)
    void performanceTest() throws InterruptedException {
        // Given
        startVideoServer();
        
        // When - 많은 프레임 전송
        long startTime = System.currentTimeMillis();
        int frameCount = 50;
        
        sendTestFramesToServer(frameCount);
        
        Thread.sleep(5000); // 처리 대기
        long endTime = System.currentTimeMillis();
        
        // Then - 성능 확인
        long processingTime = endTime - startTime;
        double framesPerSecond = frameCount * 1000.0 / processingTime;
        
        assertTrue(videoServer.getTotalFramesReceived() > frameCount * 0.8, 
            "대부분의 프레임이 수신되어야 함");
        assertTrue(framesPerSecond > 5.0, 
            "초당 최소 5프레임 이상 처리되어야 함");
        
        System.out.println("Performance: " + framesPerSecond + " FPS");
    }

    // 헬퍼 메서드들
    
    private void startVideoServer() throws InterruptedException {
        startVideoServerWithPort(testPort);
    }
    
    private void startVideoServerWithPort(int port) throws InterruptedException {
        videoServer = new VideoServerThread(port, testPanel);
        
        videoServer.setEventListener(new VideoServerThread.ServerEventListener() {
            @Override
            public void onServerStarted(int serverPort) {
                serverStarted.set(true);
            }
            
            @Override
            public void onClientConnected(String clientId) {
                clientConnections.incrementAndGet();
            }
            
            @Override
            public void onFrameReceived(BufferedImage frame, String clientId) {
                frameReceived.set(true);
            }
        });
        
        videoServer.start();
        Thread.sleep(1000); // 서버 시작 대기
    }
    
    private void sendTestFramesToServer(int frameCount) {
        new Thread(() -> {
            try {
                for (int i = 0; i < frameCount; i++) {
                    sendSingleTestFrame(i + 1);
                    Thread.sleep(100); // 프레임 간 간격
                }
            } catch (Exception e) {
                System.err.println("Error sending test frames: " + e.getMessage());
            }
        }).start();
    }
    
    private void sendSingleTestFrame(int frameNumber) throws IOException {
        // 테스트용 이미지 생성
        BufferedImage testImage = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        
        // 이미지에 간단한 패턴 그리기 (프레임 번호 표시)
        for (int x = 0; x < testImage.getWidth(); x++) {
            for (int y = 0; y < testImage.getHeight(); y++) {
                int color = ((x + y + frameNumber) % 256) << 16 | 
                           ((x * y + frameNumber) % 256) << 8 | 
                           ((frameNumber * 10) % 256);
                testImage.setRGB(x, y, color);
            }
        }
        
        // 이미지를 바이트 배열로 변환
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();
        
        // 서버로 프레임 전송
        try (Socket socket = new Socket("localhost", testPort);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            
            VideoServerThread.Frame frame = new VideoServerThread.Frame(imageData, frameNumber);
            oos.writeObject(frame);
            oos.flush();
            
        } catch (IOException e) {
            // 연결 실패는 테스트에서 예상될 수 있음
            System.err.println("Failed to send frame " + frameNumber + ": " + e.getMessage());
        }
    }
}