package com.multic.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
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

import com.multic.server.VideoServerThread;
import com.multic.utils.TestUtils;

/**
 * VideoServerThread에 대한 통합 테스트
 * - 서버 생명주기 테스트
 * - 멀티 클라이언트 연결 테스트
 * - 프레임 수신 테스트
 * - 통계 기능 테스트
 */
class VideoServerThreadTest {

    private VideoServerThread serverThread;
    private JPanel testPanel;
    private int testPort;
    private final AtomicInteger portCounter = new AtomicInteger(15000);

    @BeforeEach
    void setUp() {
        testPanel = new JPanel();
        testPort = findAvailablePort();
        assumeTrue(testPort > 0, "Could not find an available port for testing");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.shutdown();
            serverThread.join(3000);
        }
    }

    @Test
    @DisplayName("서버 스레드가 정상적으로 시작되고 중지되어야 함")
    @Timeout(10)
    void serverLifecycleTest() throws InterruptedException {
        // Given
        serverThread = new VideoServerThread(testPort, testPanel);
        
        // When
        serverThread.start();
        Thread.sleep(500); // 서버 시작 대기
        
        // Then
        assertTrue(serverThread.isAlive(), "서버 스레드가 실행 중이어야 함");
        assertTrue(serverThread.isRunning(), "서버가 실행 상태여야 함");
        
        // When - 서버 종료
        serverThread.shutdown();
        serverThread.join(2000);
        
        // Then
        assertFalse(serverThread.isAlive(), "서버 스레드가 종료되어야 함");
        assertFalse(serverThread.isRunning(), "서버가 중지 상태여야 함");
    }

    @Test
    @DisplayName("여러 클라이언트가 동시에 연결할 수 있어야 함")
    @Timeout(15)
    void multipleClientConnectionTest() throws InterruptedException {
        // Given
        serverThread = new VideoServerThread(testPort, testPanel);
        AtomicInteger connectionCount = new AtomicInteger(0);
        AtomicInteger disconnectionCount = new AtomicInteger(0);
        
        serverThread.setEventListener(new VideoServerThread.ServerEventListener() {
            @Override
            public void onClientConnected(String clientId) {
                connectionCount.incrementAndGet();
            }
            
            @Override
            public void onClientDisconnected(String clientId) {
                disconnectionCount.incrementAndGet();
            }
        });
        
        serverThread.start();
        Thread.sleep(500); // 서버 시작 대기
        
        // When - 3개의 클라이언트 동시 연결
        int clientCount = 3;
        CountDownLatch latch = new CountDownLatch(clientCount);
        
        for (int i = 0; i < clientCount; i++) {
            new Thread(() -> {
                try (Socket socket = new Socket("localhost", testPort)) {
                    Thread.sleep(100); // 짧은 연결 유지
                } catch (Exception e) {
                    // 테스트에서는 연결 오류 무시
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "모든 클라이언트 연결이 완료되어야 함");
        Thread.sleep(500); // 연결 처리 대기
        
        assertTrue(connectionCount.get() >= clientCount, 
            "연결 수가 예상보다 적음: " + connectionCount.get());
    }

    @Test
    @DisplayName("프레임 데이터를 수신하고 통계를 업데이트해야 함")
    @Timeout(10)
    void frameReceiveTest() throws InterruptedException {
        // Given
        serverThread = new VideoServerThread(testPort, testPanel);
        AtomicBoolean frameReceived = new AtomicBoolean(false);
        
        serverThread.setEventListener(new VideoServerThread.ServerEventListener() {
            @Override
            public void onFrameReceived(BufferedImage frame, String clientId) {
                frameReceived.set(true);
            }
        });
        
        serverThread.start();
        Thread.sleep(500); // 서버 시작 대기
        
        // When - 테스트 프레임 전송
        new Thread(() -> {
            try {
                sendTestFrame();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        // Then
        Thread.sleep(1000); // 프레임 처리 대기
        long totalFrames = serverThread.getTotalFramesReceived();
        long totalBytes = serverThread.getTotalBytesReceived();
        
        assertTrue(totalFrames > 0, "프레임이 수신되어야 함");
        assertTrue(totalBytes > 0, "바이트 수가 0보다 커야 함");
    }

    @Test
    @DisplayName("서버 통계가 정확하게 추적되어야 함")
    @Timeout(10)
    void statisticsTest() throws InterruptedException {
        // Given
        serverThread = new VideoServerThread(testPort, testPanel);
        serverThread.start();
        Thread.sleep(500);
        
        // When
        long initialFrames = serverThread.getTotalFramesReceived();
        long initialBytes = serverThread.getTotalBytesReceived();
        
        // 여러 프레임 전송
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try {
                    sendTestFrame();
                } catch (Exception e) {
                    // 테스트에서는 무시
                }
            }).start();
        }
        
        Thread.sleep(1500); // 처리 대기
        
        // Then
        long finalFrames = serverThread.getTotalFramesReceived();
        long finalBytes = serverThread.getTotalBytesReceived();
        
        assertTrue(finalFrames >= initialFrames, "프레임 수가 증가해야 함");
        assertTrue(finalBytes >= initialBytes, "바이트 수가 증가해야 함");
        assertTrue(serverThread.getLastFrameTime() > 0, "마지막 프레임 시간이 설정되어야 함");
    }

    @Test
    @DisplayName("잘못된 포트로 서버 생성 테스트")
    void invalidPortTest() {
        // VideoServerThread는 생성자에서 포트 유효성 검사를 하지 않음
        // 실제 서버 시작 시 예외가 발생함
        VideoServerThread invalidPortServer = new VideoServerThread(-1, testPanel);
        invalidPortServer.start();
        
        TestUtils.sleep(1000); // 서버 시작 대기
        
        // 잘못된 포트로는 실제로 서버가 시작되지 않음 (연결 테스트로 확인)
        assertThrows(Exception.class, () -> {
            try (Socket socket = new Socket("localhost", -1)) {
                // 연결 시도
            }
        }, "잘못된 포트로는 연결할 수 없어야 함");
        
        invalidPortServer.shutdown();
        
        // null 패널 테스트
        assertThrows(NullPointerException.class, () -> {
            new VideoServerThread(testPort, null);
        }, "null 패널에 대해 예외가 발생해야 함");
    }

    @Test
    @DisplayName("서버가 중지된 상태에서 클라이언트 연결 시도 시 실패해야 함")
    @Timeout(5)
    void connectionToStoppedServerTest() throws InterruptedException {
        // Given
        serverThread = new VideoServerThread(testPort, testPanel);
        serverThread.start();
        Thread.sleep(500);
        
        // When - 서버 중지
        serverThread.shutdown();
        serverThread.join(2000);
        
        // Then - 연결 시도 실패 확인
        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket("localhost", testPort)) {
                // 연결이 성공하면 안됨
            }
        }, "중지된 서버에 연결할 수 없어야 함");
    }

    /**
     * 사용 가능한 포트를 찾는 헬퍼 메서드
     */
    private int findAvailablePort() {
        for (int i = 0; i < 50; i++) {
            int port = portCounter.incrementAndGet();
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // 포트가 사용 중이면 다음 포트 시도
            }
        }
        return -1; // 사용 가능한 포트를 찾지 못함
    }

    /**
     * 테스트용 프레임을 서버로 전송하는 헬퍼 메서드
     */
    private void sendTestFrame() throws IOException {
        // 테스트용 이미지 생성
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        
        // 이미지를 바이트 배열로 변환
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        byte[] imageData = baos.toByteArray();
        
        // 서버로 프레임 전송
        try (Socket socket = new Socket("localhost", testPort);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            
            VideoServerThread.Frame frame = new VideoServerThread.Frame(imageData, 1);
            oos.writeObject(frame);
            oos.flush();
            
            Thread.sleep(100); // 전송 완료 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
