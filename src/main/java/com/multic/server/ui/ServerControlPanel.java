package com.multic.server.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * 향상된 서버 UI 패널
 * - 비디오 표시 영역
 * - 서버 상태 정보
 * - 클라이언트 연결 정보
 * - 통계 정보
 * - 제어 버튼들
 */
public class ServerControlPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	
	// 비디오 표시 영역
	private VideoDisplayPanel videoPanel;
	
	// 상태 정보 레이블들
	private JLabel serverStatusLabel;
	private JLabel clientCountLabel;
	private JLabel totalFramesLabel;
	private JLabel totalBytesLabel;
	private JLabel lastFrameTimeLabel;
	private JLabel fpsLabel;
	
	// 제어 버튼들
	private JButton startServerButton;
	private JButton stopServerButton;
	private JButton startCameraButton;
	private JButton stopCameraButton;
	private JButton captureButton;
	
	// 로그 영역
	private JTextArea logArea;
	
	// 통계 추적
	private long lastFrameCount = 0;
	private long lastUpdateTime = System.currentTimeMillis();
	private Timer updateTimer;
	
	// 콜백 인터페이스
	public interface ControlPanelListener {
		void onStartServer();
		void onStopServer();
		void onStartCamera();
		void onStopCamera();
		void onCapture();
	}
	
	private ControlPanelListener listener;
	
	public ServerControlPanel() {
		initializeUI();
		startUpdateTimer();
	}
	
	public void setListener(ControlPanelListener listener) {
		this.listener = listener;
	}
	
	private void initializeUI() {
		setLayout(new BorderLayout());
		
		// 메인 컨텐츠 영역
		JPanel mainPanel = new JPanel(new BorderLayout());
		
		// 비디오 표시 영역 (중앙)
		videoPanel = new VideoDisplayPanel();
		videoPanel.setPreferredSize(new Dimension(640, 480));
		videoPanel.setBorder(BorderFactory.createTitledBorder("Video Stream"));
		mainPanel.add(videoPanel, BorderLayout.CENTER);
		
		// 오른쪽 제어 패널
		JPanel rightPanel = createRightPanel();
		mainPanel.add(rightPanel, BorderLayout.EAST);
		
		// 하단 로그 패널
		JPanel bottomPanel = createBottomPanel();
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
		
		add(mainPanel, BorderLayout.CENTER);
	}
	
	private JPanel createRightPanel() {
		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setPreferredSize(new Dimension(300, 0));
		
		// 상태 정보 패널
		JPanel statusPanel = createStatusPanel();
		rightPanel.add(statusPanel, BorderLayout.NORTH);
		
		// 제어 버튼 패널
		JPanel controlPanel = createControlPanel();
		rightPanel.add(controlPanel, BorderLayout.CENTER);
		
		return rightPanel;
	}
	
	private JPanel createStatusPanel() {
		JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
		panel.setBorder(BorderFactory.createTitledBorder("Server Status"));
		panel.setPreferredSize(new Dimension(280, 200));
		
		// 상태 레이블들 초기화
		serverStatusLabel = createStatusLabel("Server: Stopped", Color.RED);
		clientCountLabel = createStatusLabel("Clients: 0", Color.BLACK);
		totalFramesLabel = createStatusLabel("Total Frames: 0", Color.BLACK);
		totalBytesLabel = createStatusLabel("Total Bytes: 0 KB", Color.BLACK);
		lastFrameTimeLabel = createStatusLabel("Last Frame: Never", Color.BLACK);
		fpsLabel = createStatusLabel("FPS: 0", Color.BLACK);
		
		panel.add(serverStatusLabel);
		panel.add(clientCountLabel);
		panel.add(totalFramesLabel);
		panel.add(totalBytesLabel);
		panel.add(lastFrameTimeLabel);
		panel.add(fpsLabel);
		
		return panel;
	}
	
	private JLabel createStatusLabel(String text, Color color) {
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		return label;
	}
	
	private JPanel createControlPanel() {
		JPanel panel = new JPanel(new GridLayout(0, 1, 5, 10));
		panel.setBorder(BorderFactory.createTitledBorder("Controls"));
		
		// 서버 제어 버튼들
		startServerButton = new JButton("Start Server");
		stopServerButton = new JButton("Stop Server");
		
		// 카메라 제어 버튼들
		startCameraButton = new JButton("Start Camera");
		stopCameraButton = new JButton("Stop Camera");
		
		// 캡처 버튼
		captureButton = new JButton("Capture Snapshot");
		
		// 초기 상태 설정
		stopServerButton.setEnabled(false);
		stopCameraButton.setEnabled(false);
		captureButton.setEnabled(false);
		
		// 이벤트 리스너 추가
		startServerButton.addActionListener(e -> {
			if (listener != null) listener.onStartServer();
		});
		
		stopServerButton.addActionListener(e -> {
			if (listener != null) listener.onStopServer();
		});
		
		startCameraButton.addActionListener(e -> {
			if (listener != null) listener.onStartCamera();
		});
		
		stopCameraButton.addActionListener(e -> {
			if (listener != null) listener.onStopCamera();
		});
		
		captureButton.addActionListener(e -> {
			if (listener != null) listener.onCapture();
		});
		
		panel.add(startServerButton);
		panel.add(stopServerButton);
		panel.add(new JLabel(" ")); // 공백
		panel.add(startCameraButton);
		panel.add(stopCameraButton);
		panel.add(new JLabel(" ")); // 공백
		panel.add(captureButton);
		
		return panel;
	}
	
	private JPanel createBottomPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setPreferredSize(new Dimension(0, 150));
		panel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
		
		logArea = new JTextArea(8, 50);
		logArea.setEditable(false);
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		logArea.setBackground(Color.BLACK);
		logArea.setForeground(Color.GREEN);
		
		JScrollPane scrollPane = new JScrollPane(logArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		panel.add(scrollPane, BorderLayout.CENTER);
		
		return panel;
	}
	
	private void startUpdateTimer() {
		updateTimer = new Timer(true);
		updateTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(() -> updateDisplay());
			}
		}, 0, 1000); // 1초마다 업데이트
	}
	
	private void updateDisplay() {
		// FPS 계산 (이 메서드는 외부에서 프레임 수를 받아와야 함)
		long currentTime = System.currentTimeMillis();
		long timeDiff = currentTime - lastUpdateTime;
		
		if (timeDiff >= 1000) {
			lastUpdateTime = currentTime;
		}
	}
	
	// 비디오 표시 패널
	private static class VideoDisplayPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private BufferedImage currentFrame;
		private String overlayText = "No video stream";
		
		public VideoDisplayPanel() {
			setBackground(Color.BLACK);
		}
		
		public void updateFrame(BufferedImage frame) {
			this.currentFrame = frame;
			this.overlayText = null;
			repaint();
		}
		
		public void setOverlayText(String text) {
			this.overlayText = text;
			repaint();
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			if (currentFrame != null) {
				// 화면 크기에 맞게 이미지 스케일링
				int panelWidth = getWidth();
				int panelHeight = getHeight();
				
				double scaleX = (double) panelWidth / currentFrame.getWidth();
				double scaleY = (double) panelHeight / currentFrame.getHeight();
				double scale = Math.min(scaleX, scaleY);
				
				int scaledWidth = (int) (currentFrame.getWidth() * scale);
				int scaledHeight = (int) (currentFrame.getHeight() * scale);
				
				int x = (panelWidth - scaledWidth) / 2;
				int y = (panelHeight - scaledHeight) / 2;
				
				g.drawImage(currentFrame, x, y, scaledWidth, scaledHeight, null);
			}
			
			// 오버레이 텍스트 표시
			if (overlayText != null) {
				g.setColor(Color.WHITE);
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
				
				int textWidth = g.getFontMetrics().stringWidth(overlayText);
				int textHeight = g.getFontMetrics().getHeight();
				
				int x = (getWidth() - textWidth) / 2;
				int y = (getHeight() - textHeight) / 2 + g.getFontMetrics().getAscent();
				
				// 텍스트 배경
				g.setColor(new Color(0, 0, 0, 128));
				g.fillRect(x - 10, y - g.getFontMetrics().getAscent() - 5, 
						  textWidth + 20, textHeight + 10);
				
				// 텍스트
				g.setColor(Color.WHITE);
				g.drawString(overlayText, x, y);
			}
		}
	}
	
	// 공개 메서드들 - 외부에서 상태 업데이트
	public void updateServerStatus(boolean running) {
		SwingUtilities.invokeLater(() -> {
			if (running) {
				serverStatusLabel.setText("Server: Running");
				serverStatusLabel.setForeground(Color.GREEN);
				startServerButton.setEnabled(false);
				stopServerButton.setEnabled(true);
			} else {
				serverStatusLabel.setText("Server: Stopped");
				serverStatusLabel.setForeground(Color.RED);
				startServerButton.setEnabled(true);
				stopServerButton.setEnabled(false);
			}
		});
	}
	
	public void updateCameraStatus(boolean running) {
		SwingUtilities.invokeLater(() -> {
			if (running) {
				startCameraButton.setEnabled(false);
				stopCameraButton.setEnabled(true);
				captureButton.setEnabled(true);
				videoPanel.setOverlayText("Camera starting...");
			} else {
				startCameraButton.setEnabled(true);
				stopCameraButton.setEnabled(false);
				captureButton.setEnabled(false);
				videoPanel.setOverlayText("No video stream");
			}
		});
	}
	
	public void updateClientCount(int count) {
		SwingUtilities.invokeLater(() -> {
			clientCountLabel.setText("Clients: " + count);
		});
	}
	
	public void updateFrameStats(long totalFrames, long totalBytes, long lastFrameTime) {
		SwingUtilities.invokeLater(() -> {
			totalFramesLabel.setText("Total Frames: " + totalFrames);
			totalBytesLabel.setText("Total Bytes: " + formatBytes(totalBytes));
			
			if (lastFrameTime > 0) {
				SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
				lastFrameTimeLabel.setText("Last Frame: " + sdf.format(new Date(lastFrameTime)));
			}
		});
	}
	
	public void updateFps(double fps) {
		SwingUtilities.invokeLater(() -> {
			fpsLabel.setText(String.format("FPS: %.1f", fps));
		});
	}
	
	public void displayFrame(BufferedImage frame) {
		SwingUtilities.invokeLater(() -> {
			videoPanel.updateFrame(frame);
		});
	}
	
	public void addLogMessage(String message) {
		SwingUtilities.invokeLater(() -> {
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			String timestamp = sdf.format(new Date());
			String logLine = "[" + timestamp + "] " + message + "\n";
			
			logArea.append(logLine);
			logArea.setCaretPosition(logArea.getDocument().getLength());
			
			// 로그가 너무 길어지면 앞부분 삭제
			if (logArea.getLineCount() > 100) {
				try {
					int end = logArea.getLineEndOffset(10);
					logArea.getDocument().remove(0, end);
				} catch (Exception e) {
					// 무시
				}
			}
		});
	}
	
	private String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		} else {
			return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
		}
	}
	
	// 정리
	public void cleanup() {
		if (updateTimer != null) {
			updateTimer.cancel();
		}
	}
}