package com.multic.server;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * 업그레이드된 비디오 서버 스레드
 * - 멀티 클라이언트 지원
 * - 연결 관리 개선
 * - 성능 최적화
 * - 통계 및 모니터링 기능
 */
public class VideoServerThread extends Thread {
	private final int videoServerPort;
	private final JPanel panel;
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	// 멀티 클라이언트 지원
	private final ConcurrentHashMap<String, ClientConnection> clients = new ConcurrentHashMap<>();
	private final ExecutorService clientExecutor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "ClientHandler");
		t.setDaemon(true);
		return t;
	});
	
	// 통계 및 모니터링
	private final AtomicLong totalFramesReceived = new AtomicLong(0);
	private final AtomicLong totalBytesReceived = new AtomicLong(0);
	private volatile long lastFrameTime = System.currentTimeMillis();
	private volatile BufferedImage lastFrame = null;
	
	// 서버 소켓
	private volatile ServerSocket serverSocket;
	
	// 콜백 인터페이스
	public interface ServerEventListener {
		default void onClientConnected(String clientId) {}
		default void onClientDisconnected(String clientId) {}
		default void onFrameReceived(BufferedImage frame, String clientId) {}
		default void onError(Exception e) {}
		default void onServerStarted(int port) {}
		default void onServerStopped() {}
	}
	
	private volatile ServerEventListener eventListener;

	public VideoServerThread(int videoServerPort, JPanel panel) {
		super("VideoServerThread-" + videoServerPort);
		this.videoServerPort = videoServerPort;
		this.panel = Objects.requireNonNull(panel, "panel");
	}
	
	public void setEventListener(ServerEventListener listener) {
		this.eventListener = listener;
	}

	@Override
	public void run() {
		if (!running.get()) {
			return;
		}
		
		System.out.println("Enhanced Video Server starting on port " + videoServerPort);
		
		try (ServerSocket server = new ServerSocket(videoServerPort)) {
			this.serverSocket = server;
			notifyServerStarted(videoServerPort);
			
			while (running.get()) {
				try {
					Socket clientSocket = server.accept();
					handleNewClient(clientSocket);
				} catch (IOException e) {
					if (running.get()) {
						notifyError(e);
					}
				}
			}
		} catch (IOException e) {
			if (running.get()) {
				notifyError(e);
			}
		} finally {
			cleanup();
			notifyServerStopped();
		}
	}
	
	private void handleNewClient(Socket clientSocket) {
		String clientId = generateClientId(clientSocket);
		ClientConnection connection = new ClientConnection(clientId, clientSocket);
		clients.put(clientId, connection);
		
		clientExecutor.submit(() -> {
			try {
				System.out.println("Client connected: " + clientId);
				notifyClientConnected(clientId);
				handleClient(connection);
			} catch (Exception e) {
				notifyError(e);
			} finally {
				clients.remove(clientId);
				closeQuietly(clientSocket);
				System.out.println("Client disconnected: " + clientId);
				notifyClientDisconnected(clientId);
			}
		});
	}
	
	private String generateClientId(Socket socket) {
		return socket.getRemoteSocketAddress().toString() + "_" + System.currentTimeMillis();
	}

	private void handleClient(ClientConnection connection) throws IOException {
		try (InputStream in = connection.socket.getInputStream(); 
			 ObjectInputStream ois = new ObjectInputStream(in)) {
			
			while (running.get() && !connection.socket.isClosed()) {
				try {
					Frame frame = (Frame) ois.readObject();
					if (frame == null || frame.bytes == null) {
						continue;
					}
					
					processFrame(frame, connection.clientId);
				} catch (ClassNotFoundException e) {
					notifyError(e);
					break;
				}
			}
		}
	}
	
	private void processFrame(Frame frame, String clientId) {
		try (InputStream inputImage = new ByteArrayInputStream(frame.bytes)) {
			BufferedImage bufferedImage = ImageIO.read(inputImage);
			if (bufferedImage != null) {
				// 통계 업데이트
				totalFramesReceived.incrementAndGet();
				totalBytesReceived.addAndGet(frame.bytes.length);
				lastFrameTime = System.currentTimeMillis();
				lastFrame = bufferedImage;
				
				renderFrame(bufferedImage);
				notifyFrameReceived(bufferedImage, clientId);
			}
		} catch (IOException e) {
			notifyError(e);
		}
	}

	private void renderFrame(BufferedImage image) {
		SwingUtilities.invokeLater(() -> {
			Graphics graphics = panel.getGraphics();
			if (graphics != null) {
				try {
					graphics.drawImage(image, 0, 0, panel.getWidth(), panel.getHeight(), null);
				} finally {
					graphics.dispose();
				}
			}
		});
	}

	public void shutdown() {
		running.set(false);
		
		// 모든 클라이언트 연결 종료
		clients.values().forEach(client -> closeQuietly(client.socket));
		clients.clear();
		
		// 서버 소켓 종료
		closeQuietly(serverSocket);
		
		// 스레드 풀 종료
		clientExecutor.shutdown();
	}
	
	private void cleanup() {
		clients.values().forEach(client -> closeQuietly(client.socket));
		clients.clear();
		closeQuietly(serverSocket);
		clientExecutor.shutdown();
	}

	private void closeQuietly(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ignored) {
				// 무시
			}
		}
	}
	
	// 통계 및 모니터링 메서드
	public int getConnectedClientCount() {
		return clients.size();
	}
	
	public long getTotalFramesReceived() {
		return totalFramesReceived.get();
	}
	
	public long getTotalBytesReceived() {
		return totalBytesReceived.get();
	}
	
	public long getLastFrameTime() {
		return lastFrameTime;
	}
	
	public BufferedImage getLastFrame() {
		return lastFrame;
	}
	
	public boolean isRunning() {
		return running.get();
	}
	
	// 이벤트 알림 메서드
	private void notifyServerStarted(int port) {
		if (eventListener != null) {
			eventListener.onServerStarted(port);
		}
	}
	
	private void notifyServerStopped() {
		if (eventListener != null) {
			eventListener.onServerStopped();
		}
	}
	
	private void notifyClientConnected(String clientId) {
		if (eventListener != null) {
			eventListener.onClientConnected(clientId);
		}
	}
	
	private void notifyClientDisconnected(String clientId) {
		if (eventListener != null) {
			eventListener.onClientDisconnected(clientId);
		}
	}
	
	private void notifyFrameReceived(BufferedImage frame, String clientId) {
		if (eventListener != null) {
			eventListener.onFrameReceived(frame, clientId);
		}
	}
	
	private void notifyError(Exception e) {
		System.err.println("Server error: " + e.getMessage());
		if (eventListener != null) {
			eventListener.onError(e);
		}
	}
	
	// 클라이언트 연결 정보
	private static class ClientConnection {
		final String clientId;
		final Socket socket;
		final long connectTime;
		
		ClientConnection(String clientId, Socket socket) {
			this.clientId = clientId;
			this.socket = socket;
			this.connectTime = System.currentTimeMillis();
		}
	}

	public static class Frame implements Serializable {
		private static final long serialVersionUID = 1L;
		public byte[] bytes;
		public long timestamp;
		public int frameNumber;

		public Frame(byte[] bytes) {
			this.bytes = bytes;
			this.timestamp = System.currentTimeMillis();
		}
		
		public Frame(byte[] bytes, int frameNumber) {
			this.bytes = bytes;
			this.timestamp = System.currentTimeMillis();
			this.frameNumber = frameNumber;
		}

		public int size() {
			return bytes != null ? bytes.length : 0;
		}
	}
}
