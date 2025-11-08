package com.multic.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import com.multic.server.VideoServerThread;

import nu.pattern.OpenCV;

/**
 * 비디오 클라이언트
 * - 카메라에서 프레임을 캡처하여 서버로 전송
 * - 연결 관리 및 재연결 기능
 * - 전송 통계 및 모니터링
 */
public class VideoClient {
    
    private final String serverHost;
    private final int serverPort;
    private final int cameraIndex;
    private final double frameRate;
    private final double jpegQuality;
    
    // 상태 관리
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cameraOpened = new AtomicBoolean(false);
    
    // OpenCV 관련
    private VideoCapture camera;
    
    // 전송 스케줄러
    private ScheduledExecutorService scheduler;
    
    // 통계
    private final AtomicLong framesSent = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private volatile long lastFrameTime = 0;
    
    // 이벤트 리스너
    public interface ClientEventListener {
        default void onConnected() {}
        default void onDisconnected() {}
        default void onFrameSent(long frameNumber, int frameSize) {}
        default void onError(Exception e) {}
        default void onCameraOpened(int width, int height) {}
        default void onCameraClosed() {}
    }
    
    private volatile ClientEventListener eventListener;
    
    public VideoClient(String serverHost, int serverPort, int cameraIndex) {
        this(serverHost, serverPort, cameraIndex, 30.0, 0.8);
    }
    
    public VideoClient(String serverHost, int serverPort, int cameraIndex, 
                      double frameRate, double jpegQuality) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.cameraIndex = cameraIndex;
        this.frameRate = frameRate;
        this.jpegQuality = jpegQuality;
        
        // OpenCV 로드
        try {
            OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully for VideoClient");
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
            throw new RuntimeException("OpenCV initialization failed", e);
        }
    }
    
    public void setEventListener(ClientEventListener listener) {
        this.eventListener = listener;
    }
    
    /**
     * 클라이언트 시작
     */
    public synchronized boolean start() {
        if (running.get()) {
            return true;
        }
        
        System.out.println("Starting video client...");
        
        // 카메라 열기
        if (!openCamera()) {
            return false;
        }
        
        running.set(true);
        
        // 프레임 전송 스케줄러 시작
        startFrameScheduler();
        
        // 이벤트 알림
        if (eventListener != null) {
            eventListener.onConnected();
        }
        
        System.out.println("Video client started successfully");
        return true;
    }
    
    /**
     * 클라이언트 중지
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        System.out.println("Stopping video client...");
        
        running.set(false);
        
        // 스케줄러 종료
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 카메라 닫기
        closeCamera();
        
        // 이벤트 알림
        if (eventListener != null) {
            eventListener.onDisconnected();
        }
        
        System.out.println("Video client stopped");
    }
    
    /**
     * 카메라 열기
     */
    private boolean openCamera() {
        try {
            camera = new VideoCapture(cameraIndex);
            
            if (!camera.isOpened()) {
                System.err.println("Failed to open camera: " + cameraIndex);
                return false;
            }
            
            // 카메라 설정
            camera.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
            camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            camera.set(Videoio.CAP_PROP_FPS, frameRate);
            
            cameraOpened.set(true);
            
            // 카메라 정보 가져오기
            double width = camera.get(Videoio.CAP_PROP_FRAME_WIDTH);
            double height = camera.get(Videoio.CAP_PROP_FRAME_HEIGHT);
            
            System.out.println("Camera opened: " + (int)width + "x" + (int)height + " @ " + frameRate + "fps");
            
            // 이벤트 알림
            if (eventListener != null) {
                eventListener.onCameraOpened((int)width, (int)height);
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error opening camera: " + e.getMessage());
            if (eventListener != null) {
                eventListener.onError(e);
            }
            return false;
        }
    }
    
    /**
     * 카메라 닫기
     */
    private void closeCamera() {
        if (camera != null && cameraOpened.get()) {
            camera.release();
            cameraOpened.set(false);
            
            if (eventListener != null) {
                eventListener.onCameraClosed();
            }
            
            System.out.println("Camera closed");
        }
    }
    
    /**
     * 프레임 전송 스케줄러 시작
     */
    private void startFrameScheduler() {
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "FrameSender");
            t.setDaemon(true);
            return t;
        });
        
        // 프레임 전송 주기 계산 (밀리초)
        long period = Math.round(1000.0 / frameRate);
        
        scheduler.scheduleAtFixedRate(this::captureAndSendFrame, 
                                     100, period, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 프레임 캡처 및 전송
     */
    private void captureAndSendFrame() {
        if (!running.get() || !cameraOpened.get()) {
            return;
        }
        
        try {
            Mat frame = new Mat();
            
            // 프레임 캡처
            if (!camera.read(frame) || frame.empty()) {
                System.err.println("Failed to capture frame");
                return;
            }
            
            // JPEG로 인코딩
            MatOfByte matOfByte = new MatOfByte();
            if (!Imgcodecs.imencode(".jpg", frame, matOfByte)) {
                System.err.println("Failed to encode frame to JPEG");
                return;
            }
            
            byte[] frameData = matOfByte.toArray();
            
            // 서버로 전송
            sendFrameToServer(frameData);
            
            // 통계 업데이트
            long frameNumber = framesSent.incrementAndGet();
            bytesSent.addAndGet(frameData.length);
            lastFrameTime = System.currentTimeMillis();
            
            // 이벤트 알림
            if (eventListener != null) {
                eventListener.onFrameSent(frameNumber, frameData.length);
            }
            
            // 메모리 정리
            frame.release();
            
        } catch (Exception e) {
            System.err.println("Error capturing/sending frame: " + e.getMessage());
            if (eventListener != null) {
                eventListener.onError(e);
            }
        }
    }
    
    /**
     * 프레임을 서버로 전송
     */
    private void sendFrameToServer(byte[] frameData) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            
            VideoServerThread.Frame frame = new VideoServerThread.Frame(frameData, (int)framesSent.get());
            oos.writeObject(frame);
            oos.flush();
            
        } catch (IOException e) {
            // 연결 실패 시 로그만 출력 (재시도는 다음 프레임에서)
            System.err.println("Failed to send frame to server: " + e.getMessage());
            throw e;
        }
    }
    
    // Getter 메서드들
    
    public boolean isRunning() {
        return running.get();
    }
    
    public boolean isCameraOpened() {
        return cameraOpened.get();
    }
    
    public long getFramesSent() {
        return framesSent.get();
    }
    
    public long getBytesSent() {
        return bytesSent.get();
    }
    
    public long getLastFrameTime() {
        return lastFrameTime;
    }
    
    public String getServerHost() {
        return serverHost;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    public int getCameraIndex() {
        return cameraIndex;
    }
    
    public double getFrameRate() {
        return frameRate;
    }
    
    public double getJpegQuality() {
        return jpegQuality;
    }
    
    /**
     * 통계 정보 출력
     */
    public void printStatistics() {
        long frames = getFramesSent();
        long bytes = getBytesSent();
        long uptime = lastFrameTime > 0 ? (System.currentTimeMillis() - lastFrameTime) / 1000 : 0;
        
        System.out.println("=== Video Client Statistics ===");
        System.out.println("Server: " + serverHost + ":" + serverPort);
        System.out.println("Camera: " + cameraIndex + " (" + frameRate + " fps)");
        System.out.println("Frames sent: " + frames);
        System.out.println("Bytes sent: " + String.format("%.2f KB", bytes / 1024.0));
        System.out.println("Running: " + (running.get() ? "Yes" : "No"));
        System.out.println("Camera opened: " + (cameraOpened.get() ? "Yes" : "No"));
        System.out.println("Last frame: " + (uptime < 5 ? "Just now" : uptime + "s ago"));
        System.out.println("================================");
    }
}