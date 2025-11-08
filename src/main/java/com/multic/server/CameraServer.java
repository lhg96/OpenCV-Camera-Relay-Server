package com.multic.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import nu.pattern.OpenCV;

/**
 * 통합 카메라 서버
 * - 카메라 캡처 기능
 * - 클라이언트로 스트리밍 기능
 * - 로컬 비디오 서버와 연동
 */
public class CameraServer {
	
	// 카메라 관련
	private VideoCapture capture;
	private Mat image;
	private Thread cameraWorker;
	private final AtomicBoolean cameraRunning = new AtomicBoolean(false);
	
	// 스트리밍 관련
	private final AtomicBoolean streamingEnabled = new AtomicBoolean(false);
	private final ScheduledExecutorService streamExecutor = Executors.newScheduledThreadPool(2);
	private final AtomicInteger frameNumber = new AtomicInteger(0);
	
	// 설정
	private int cameraIndex = 0;
	private int frameRate = 30; // FPS
	private double jpegQuality = 0.8;
	private String serverHost = "localhost";
	private int serverPort = 5252;
	
	// 콜백 인터페이스
	public interface CameraEventListener {
		default void onCameraStarted() {}
		default void onCameraStopped() {}
		default void onFrameCaptured(BufferedImage frame) {}
		default void onStreamingStarted() {}
		default void onStreamingStopped() {}
		default void onError(Exception e) {}
	}
	
	private volatile CameraEventListener eventListener;
	
	public CameraServer() {
		initializeOpenCV();
	}
	
	public CameraServer(int cameraIndex, int frameRate, double jpegQuality) {
		this.cameraIndex = cameraIndex;
		this.frameRate = frameRate;
		this.jpegQuality = jpegQuality;
		initializeOpenCV();
	}
	
	private void initializeOpenCV() {
		try {
			OpenCV.loadShared();
			System.out.println("OpenCV loaded successfully for CameraServer");
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Failed to load OpenCV: " + e.getMessage());
		}
	}
	
	public void setEventListener(CameraEventListener listener) {
		this.eventListener = listener;
	}
	
	/**
	 * 카메라 시작
	 */
	public boolean startCamera() {
		if (cameraRunning.get()) {
			return true;
		}
		
		capture = new VideoCapture(cameraIndex);
		if (!capture.isOpened()) {
			notifyError(new RuntimeException("Failed to open camera: " + cameraIndex));
			return false;
		}
		
		cameraRunning.set(true);
		cameraWorker = new Thread(this::cameraLoop, "CameraServer-CaptureLoop");
		cameraWorker.start();
		
		notifyEvent(() -> eventListener.onCameraStarted());
		return true;
	}
	
	/**
	 * 카메라 중지
	 */
	public void stopCamera() {
		cameraRunning.set(false);
		
		if (cameraWorker != null && cameraWorker.isAlive()) {
			try {
				cameraWorker.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		releaseCamera();
		notifyEvent(() -> eventListener.onCameraStopped());
	}
	
	/**
	 * 스트리밍 시작
	 */
	public void startStreaming() {
		if (streamingEnabled.get()) {
			return;
		}
		
		streamingEnabled.set(true);
		
		// 스트리밍 스케줄러 시작
		long delayMs = 1000 / frameRate;
		streamExecutor.scheduleAtFixedRate(this::streamFrame, 0, delayMs, TimeUnit.MILLISECONDS);
		
		notifyEvent(() -> eventListener.onStreamingStarted());
	}
	
	/**
	 * 스트리밍 중지
	 */
	public void stopStreaming() {
		streamingEnabled.set(false);
		notifyEvent(() -> eventListener.onStreamingStopped());
	}
	
	/**
	 * 카메라 루프
	 */
	private void cameraLoop() {
		image = new Mat();
		final MatOfByte buffer = new MatOfByte();
		
		try {
			while (cameraRunning.get()) {
				if (!capture.read(image)) {
					Thread.sleep(10);
					continue;
				}
				
				// 이미지를 BufferedImage로 변환
				Imgcodecs.imencode(".jpg", image, buffer);
				byte[] imageData = buffer.toArray();
				
				try {
					BufferedImage bufferedImage = ImageIO.read(new java.io.ByteArrayInputStream(imageData));
					if (bufferedImage != null) {
						notifyFrameCaptured(bufferedImage);
					}
				} catch (IOException e) {
					notifyError(e);
				}
				
				// FPS 제어
				Thread.sleep(1000 / frameRate);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			buffer.release();
			releaseCamera();
		}
	}
	
	/**
	 * 프레임을 서버로 스트리밍
	 */
	private void streamFrame() {
		if (!streamingEnabled.get() || !cameraRunning.get() || image == null) {
			return;
		}
		
		try {
			// 현재 프레임을 JPEG로 인코딩
			MatOfByte buffer = new MatOfByte();
			Imgcodecs.imencode(".jpg", image, buffer);
			byte[] frameData = buffer.toArray();
			buffer.release();
			
			// 서버로 전송
			sendFrameToServer(frameData);
			
		} catch (Exception e) {
			notifyError(e);
		}
	}
	
	/**
	 * 프레임을 서버로 전송
	 */
	private void sendFrameToServer(byte[] frameData) {
		try (Socket socket = new Socket(serverHost, serverPort);
			 OutputStream out = socket.getOutputStream();
			 ObjectOutputStream oos = new ObjectOutputStream(out)) {
			
			VideoServerThread.Frame frame = new VideoServerThread.Frame(frameData, frameNumber.incrementAndGet());
			oos.writeObject(frame);
			oos.flush();
			
		} catch (IOException e) {
			// 연결 실패는 로그만 남기고 계속 진행
			System.err.println("Failed to send frame to server: " + e.getMessage());
		}
	}
	
	/**
	 * 스냅샷 촬영
	 */
	public BufferedImage captureSnapshot() {
		if (!cameraRunning.get() || image == null) {
			return null;
		}
		
		try {
			MatOfByte buffer = new MatOfByte();
			Imgcodecs.imencode(".jpg", image, buffer);
			byte[] imageData = buffer.toArray();
			buffer.release();
			
			return ImageIO.read(new java.io.ByteArrayInputStream(imageData));
		} catch (IOException e) {
			notifyError(e);
			return null;
		}
	}
	
	/**
	 * 스냅샷을 파일로 저장
	 */
	public boolean saveSnapshot(String filePath) {
		if (!cameraRunning.get() || image == null) {
			return false;
		}
		
		return Imgcodecs.imwrite(filePath, image);
	}
	
	/**
	 * 리소스 정리
	 */
	private void releaseCamera() {
		if (capture != null) {
			capture.release();
			capture = null;
		}
		
		if (image != null) {
			image.release();
			image = null;
		}
	}
	
	/**
	 * 서버 종료
	 */
	public void shutdown() {
		stopStreaming();
		stopCamera();
		streamExecutor.shutdown();
		
		try {
			if (!streamExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
				streamExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			streamExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	// 이벤트 알림 메서드
	private void notifyEvent(Runnable event) {
		if (eventListener != null) {
			SwingUtilities.invokeLater(event);
		}
	}
	
	private void notifyFrameCaptured(BufferedImage frame) {
		if (eventListener != null) {
			SwingUtilities.invokeLater(() -> eventListener.onFrameCaptured(frame));
		}
	}
	
	private void notifyError(Exception e) {
		System.err.println("CameraServer error: " + e.getMessage());
		if (eventListener != null) {
			SwingUtilities.invokeLater(() -> eventListener.onError(e));
		}
	}
	
	// Getters and Setters
	public boolean isCameraRunning() {
		return cameraRunning.get();
	}
	
	public boolean isStreamingEnabled() {
		return streamingEnabled.get();
	}
	
	public void setCameraIndex(int cameraIndex) {
		this.cameraIndex = cameraIndex;
	}
	
	public void setFrameRate(int frameRate) {
		this.frameRate = Math.max(1, Math.min(60, frameRate));
	}
	
	public void setJpegQuality(double quality) {
		this.jpegQuality = Math.max(0.1, Math.min(1.0, quality));
	}
	
	public void setServerAddress(String host, int port) {
		this.serverHost = host;
		this.serverPort = port;
	}
	
	public int getCameraIndex() { return cameraIndex; }
	public int getFrameRate() { return frameRate; }
	public double getJpegQuality() { return jpegQuality; }
	public String getServerHost() { return serverHost; }
	public int getServerPort() { return serverPort; }
	public int getCurrentFrameNumber() { return frameNumber.get(); }
}