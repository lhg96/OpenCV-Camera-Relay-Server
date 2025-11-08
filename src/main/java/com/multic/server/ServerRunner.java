package com.multic.server;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import com.multic.server.config.ServerConfig;
import com.multic.server.ui.ServerControlPanel;

/**
 * 업그레이드된 서버 런너
 * - 향상된 UI 통합
 * - 카메라 서버 통합
 * - 설정 관리
 * - 자동 시작 기능
 */
public class ServerRunner {

	private static final int DEFAULT_PORT = 5252;

	// 컴포넌트들
	private ServerConfig config;
	private VideoServerThread videoServer;
	private CameraServer cameraServer;
	private ServerControlPanel controlPanel;
	private JFrame mainFrame;
	
	// 통계 추적
	private Timer statsTimer;
	private long lastFrameCount = 0;
	private long lastUpdateTime = System.currentTimeMillis();

	public static void main(String[] args) {
		// 명령행 인수 처리
		int port = DEFAULT_PORT;
		boolean autoStart = false;
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--port") && i + 1 < args.length) {
				port = parsePort(args[++i]);
			} else if (args[i].equals("--auto-start")) {
				autoStart = true;
			} else if (args[i].equals("--help")) {
				printUsage();
				return;
			} else if (args.length == 1) {
				// 단일 인수는 포트로 간주
				port = parsePort(args[0]);
			}
		}
		
		final int finalPort = port;
		final boolean finalAutoStart = autoStart;
		
		EventQueue.invokeLater(() -> {
			ServerRunner runner = new ServerRunner();
			runner.start(finalPort, finalAutoStart);
		});
	}
	
	private static void printUsage() {
		System.out.println("Multics Camera Relay Server");
		System.out.println("Usage: java ServerRunner [options]");
		System.out.println("Options:");
		System.out.println("  --port <port>    Server port (default: 5252)");
		System.out.println("  --auto-start     Automatically start server and camera");
		System.out.println("  --help           Show this help message");
		System.out.println("Example:");
		System.out.println("  java ServerRunner --port 8080 --auto-start");
	}
	
	public void start(int port, boolean autoStart) {
		// 설정 로드
		config = new ServerConfig();
		if (port != DEFAULT_PORT) {
			config.setServerPort(port);
		}
		
		System.out.println("Starting Multics Camera Server...");
		System.out.println(config.toString());
		
		// UI 초기화
		initializeUI();
		
		// 컴포넌트 초기화
		initializeComponents();
		
		// 자동 시작
		if (autoStart || config.getAutoStartServer()) {
			startServer();
		}
		
		if (autoStart || config.getAutoStartCamera()) {
			startCamera();
		}
		
		// 통계 타이머 시작
		startStatsTimer();
		
		System.out.println("Server initialization complete.");
	}

	private static int parsePort(String arg) {
		try {
			int port = Integer.parseInt(arg);
			if (port < 1024 || port > 65535) {
				System.err.println("Port must be between 1024-65535, using default: " + DEFAULT_PORT);
				return DEFAULT_PORT;
			}
			return port;
		} catch (NumberFormatException e) {
			System.err.println("Invalid port value (" + arg + "), using default: " + DEFAULT_PORT);
			return DEFAULT_PORT;
		}
	}
	
	private void initializeUI() {
		mainFrame = new JFrame("Multics Camera Relay Server v2.0");
		
		// 컨트롤 패널 생성
		controlPanel = new ServerControlPanel();
		controlPanel.setListener(new ServerControlPanel.ControlPanelListener() {
			@Override
			public void onStartServer() {
				startServer();
			}
			
			@Override
			public void onStopServer() {
				stopServer();
			}
			
			@Override
			public void onStartCamera() {
				startCamera();
			}
			
			@Override
			public void onStopCamera() {
				stopCamera();
			}
			
			@Override
			public void onCapture() {
				captureSnapshot();
			}
		});
		
		mainFrame.add(controlPanel);
		mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		
		// 윈도우 닫기 이벤트 처리
		mainFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				shutdown();
			}
		});
		
		// 윈도우 크기 및 위치 설정
		mainFrame.setSize(new Dimension(config.getWindowWidth(), config.getWindowHeight()));
		mainFrame.setLocationRelativeTo(null);
		mainFrame.setVisible(true);
		
		controlPanel.addLogMessage("Application started");
	}
	
	private void initializeComponents() {
		// 카메라 서버 초기화
		cameraServer = new CameraServer(
			config.getCameraIndex(), 
			config.getFrameRate(), 
			config.getJpegQuality()
		);
		
		cameraServer.setServerAddress(config.getServerHost(), config.getServerPort());
		
		cameraServer.setEventListener(new CameraServer.CameraEventListener() {
			@Override
			public void onCameraStarted() {
				controlPanel.updateCameraStatus(true);
				controlPanel.addLogMessage("Camera started (index: " + config.getCameraIndex() + ")");
			}
			
			@Override
			public void onCameraStopped() {
				controlPanel.updateCameraStatus(false);
				controlPanel.addLogMessage("Camera stopped");
			}
			
			@Override
			public void onFrameCaptured(BufferedImage frame) {
				controlPanel.displayFrame(frame);
			}
			
			@Override
			public void onStreamingStarted() {
				controlPanel.addLogMessage("Streaming started");
			}
			
			@Override
			public void onStreamingStopped() {
				controlPanel.addLogMessage("Streaming stopped");
			}
			
			@Override
			public void onError(Exception e) {
				controlPanel.addLogMessage("Camera error: " + e.getMessage());
			}
		});
		
		// 비디오 서버는 필요시에만 초기화 (startServer에서)
	}
	
	private void startServer() {
		if (videoServer != null && videoServer.isRunning()) {
			controlPanel.addLogMessage("Server is already running");
			return;
		}
		
		try {
			// 더미 패널 생성 (실제 렌더링은 UI에서 처리)
			javax.swing.JPanel dummyPanel = new javax.swing.JPanel();
			dummyPanel.setPreferredSize(new Dimension(config.getVideoWidth(), config.getVideoHeight()));
			
			videoServer = new VideoServerThread(config.getServerPort(), dummyPanel);
			
			videoServer.setEventListener(new VideoServerThread.ServerEventListener() {
				@Override
				public void onServerStarted(int port) {
					controlPanel.updateServerStatus(true);
					controlPanel.addLogMessage("Video server started on port: " + port);
				}
				
				@Override
				public void onServerStopped() {
					controlPanel.updateServerStatus(false);
					controlPanel.addLogMessage("Video server stopped");
				}
				
				@Override
				public void onClientConnected(String clientId) {
					controlPanel.updateClientCount(videoServer.getConnectedClientCount());
					controlPanel.addLogMessage("Client connected: " + clientId);
				}
				
				@Override
				public void onClientDisconnected(String clientId) {
					controlPanel.updateClientCount(videoServer.getConnectedClientCount());
					controlPanel.addLogMessage("Client disconnected: " + clientId);
				}
				
				@Override
				public void onFrameReceived(BufferedImage frame, String clientId) {
					controlPanel.displayFrame(frame);
				}
				
				@Override
				public void onError(Exception e) {
					controlPanel.addLogMessage("Server error: " + e.getMessage());
				}
			});
			
			videoServer.start();
			
		} catch (Exception e) {
			controlPanel.addLogMessage("Failed to start server: " + e.getMessage());
			JOptionPane.showMessageDialog(mainFrame, 
				"Failed to start server on port " + config.getServerPort() + 
				"\nError: " + e.getMessage(), 
				"Server Error", 
				JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private void stopServer() {
		if (videoServer == null || !videoServer.isRunning()) {
			controlPanel.addLogMessage("Server is not running");
			return;
		}
		
		controlPanel.addLogMessage("Stopping video server...");
		
		new Thread(() -> {
			try {
				videoServer.shutdown();
				videoServer.join(3000);
				videoServer = null;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				controlPanel.addLogMessage("Server shutdown interrupted");
			}
		}, "ServerShutdown").start();
	}
	
	private void startCamera() {
		if (cameraServer.isCameraRunning()) {
			controlPanel.addLogMessage("Camera is already running");
			return;
		}
		
		controlPanel.addLogMessage("Starting camera...");
		
		new Thread(() -> {
			if (cameraServer.startCamera()) {
				// 스트리밍도 함께 시작 (로컬 서버가 실행 중인 경우)
				if (videoServer != null && videoServer.isRunning()) {
					cameraServer.startStreaming();
				}
			} else {
				controlPanel.addLogMessage("Failed to start camera");
			}
		}, "CameraStart").start();
	}
	
	private void stopCamera() {
		if (!cameraServer.isCameraRunning()) {
			controlPanel.addLogMessage("Camera is not running");
			return;
		}
		
		controlPanel.addLogMessage("Stopping camera...");
		cameraServer.stopCamera();
	}
	
	private void captureSnapshot() {
		if (!cameraServer.isCameraRunning()) {
			controlPanel.addLogMessage("Camera is not running - cannot capture");
			return;
		}
		
		BufferedImage snapshot = cameraServer.captureSnapshot();
		if (snapshot != null) {
			// 파일명 생성
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
			String fileName = "snapshot-" + sdf.format(new Date()) + ".jpg";
			String filePath = config.getSnapshotsPath() + "/" + fileName;
			
			// 디렉토리 생성
			File dir = new File(config.getSnapshotsPath());
			if (!dir.exists()) {
				dir.mkdirs();
			}
			
			// 파일 저장
			try {
				ImageIO.write(snapshot, "jpg", new File(filePath));
				controlPanel.addLogMessage("Snapshot saved: " + fileName);
			} catch (IOException e) {
				controlPanel.addLogMessage("Failed to save snapshot: " + e.getMessage());
			}
		} else {
			controlPanel.addLogMessage("Failed to capture snapshot");
		}
	}
	
	private void startStatsTimer() {
		statsTimer = new Timer(true);
		statsTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				updateStatistics();
			}
		}, 1000, 1000); // 1초마다 업데이트
	}
	
	private void updateStatistics() {
		if (videoServer != null && videoServer.isRunning()) {
			long currentFrames = videoServer.getTotalFramesReceived();
			long currentTime = System.currentTimeMillis();
			
			// FPS 계산
			double fps = 0;
			long timeDiff = currentTime - lastUpdateTime;
			if (timeDiff >= 1000) {
				long frameDiff = currentFrames - lastFrameCount;
				fps = (frameDiff * 1000.0) / timeDiff;
				
				lastFrameCount = currentFrames;
				lastUpdateTime = currentTime;
			}
			
			// UI 업데이트
			controlPanel.updateFrameStats(
				videoServer.getTotalFramesReceived(), 
				videoServer.getTotalBytesReceived(), 
				videoServer.getLastFrameTime()
			);
			controlPanel.updateFps(fps);
			controlPanel.updateClientCount(videoServer.getConnectedClientCount());
		}
	}
	
	private void shutdown() {
		int choice = JOptionPane.showConfirmDialog(
			mainFrame, 
			"Are you sure you want to exit?", 
			"Confirm Exit", 
			JOptionPane.YES_NO_OPTION
		);
		
		if (choice == JOptionPane.YES_OPTION) {
			controlPanel.addLogMessage("Shutting down...");
			
			// 컴포넌트 정리
			if (cameraServer != null) {
				cameraServer.shutdown();
			}
			
			if (videoServer != null) {
				videoServer.shutdown();
			}
			
			if (statsTimer != null) {
				statsTimer.cancel();
			}
			
			if (controlPanel != null) {
				controlPanel.cleanup();
			}
			
			// 설정 저장
			config.saveToFile();
			
			System.exit(0);
		}
	}
}
