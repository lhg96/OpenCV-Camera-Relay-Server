package com.multic.server.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 서버 설정 관리 클래스
 * - 포트, 카메라, 품질 등의 설정 관리
 * - 설정 파일 저장/로드
 * - 기본값 제공
 */
public class ServerConfig {
	
	private static final String CONFIG_FILE = "server.properties";
	private static final String DEFAULT_CONFIG_FILE = "server-default.properties";
	
	// 기본값 상수들
	public static final int DEFAULT_SERVER_PORT = 5252;
	public static final int DEFAULT_CAMERA_INDEX = 0;
	public static final int DEFAULT_FRAME_RATE = 30;
	public static final double DEFAULT_JPEG_QUALITY = 0.8;
	public static final String DEFAULT_SERVER_HOST = "localhost";
	public static final int DEFAULT_WINDOW_WIDTH = 1024;
	public static final int DEFAULT_WINDOW_HEIGHT = 768;
	public static final int DEFAULT_VIDEO_WIDTH = 640;
	public static final int DEFAULT_VIDEO_HEIGHT = 480;
	
	// 설정 키들
	private static final String KEY_SERVER_PORT = "server.port";
	private static final String KEY_CAMERA_INDEX = "camera.index";
	private static final String KEY_FRAME_RATE = "camera.frameRate";
	private static final String KEY_JPEG_QUALITY = "camera.jpegQuality";
	private static final String KEY_SERVER_HOST = "server.host";
	private static final String KEY_WINDOW_WIDTH = "ui.window.width";
	private static final String KEY_WINDOW_HEIGHT = "ui.window.height";
	private static final String KEY_VIDEO_WIDTH = "ui.video.width";
	private static final String KEY_VIDEO_HEIGHT = "ui.video.height";
	private static final String KEY_AUTO_START_SERVER = "server.autoStart";
	private static final String KEY_AUTO_START_CAMERA = "camera.autoStart";
	private static final String KEY_SAVE_SNAPSHOTS_PATH = "snapshots.path";
	private static final String KEY_LOG_LEVEL = "log.level";
	
	private Properties properties;
	
	public ServerConfig() {
		properties = new Properties();
		loadDefaults();
		loadFromFile();
	}
	
	/**
	 * 기본값으로 설정 초기화
	 */
	private void loadDefaults() {
		properties.setProperty(KEY_SERVER_PORT, String.valueOf(DEFAULT_SERVER_PORT));
		properties.setProperty(KEY_CAMERA_INDEX, String.valueOf(DEFAULT_CAMERA_INDEX));
		properties.setProperty(KEY_FRAME_RATE, String.valueOf(DEFAULT_FRAME_RATE));
		properties.setProperty(KEY_JPEG_QUALITY, String.valueOf(DEFAULT_JPEG_QUALITY));
		properties.setProperty(KEY_SERVER_HOST, DEFAULT_SERVER_HOST);
		properties.setProperty(KEY_WINDOW_WIDTH, String.valueOf(DEFAULT_WINDOW_WIDTH));
		properties.setProperty(KEY_WINDOW_HEIGHT, String.valueOf(DEFAULT_WINDOW_HEIGHT));
		properties.setProperty(KEY_VIDEO_WIDTH, String.valueOf(DEFAULT_VIDEO_WIDTH));
		properties.setProperty(KEY_VIDEO_HEIGHT, String.valueOf(DEFAULT_VIDEO_HEIGHT));
		properties.setProperty(KEY_AUTO_START_SERVER, "false");
		properties.setProperty(KEY_AUTO_START_CAMERA, "false");
		properties.setProperty(KEY_SAVE_SNAPSHOTS_PATH, "images");
		properties.setProperty(KEY_LOG_LEVEL, "INFO");
	}
	
	/**
	 * 파일에서 설정 로드
	 */
	public void loadFromFile() {
		File configFile = new File(CONFIG_FILE);
		if (configFile.exists()) {
			try (FileInputStream fis = new FileInputStream(configFile)) {
				properties.load(fis);
				System.out.println("Configuration loaded from: " + CONFIG_FILE);
			} catch (IOException e) {
				System.err.println("Failed to load configuration: " + e.getMessage());
			}
		} else {
			System.out.println("Configuration file not found, using defaults");
		}
	}
	
	/**
	 * 설정을 파일에 저장
	 */
	public void saveToFile() {
		try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
			properties.store(fos, "Multics Camera Server Configuration");
			System.out.println("Configuration saved to: " + CONFIG_FILE);
		} catch (IOException e) {
			System.err.println("Failed to save configuration: " + e.getMessage());
		}
	}
	
	/**
	 * 기본 설정 파일 생성
	 */
	public void createDefaultConfigFile() {
		loadDefaults();
		try (FileOutputStream fos = new FileOutputStream(DEFAULT_CONFIG_FILE)) {
			properties.store(fos, "Default Configuration for Multics Camera Server");
			System.out.println("Default configuration created: " + DEFAULT_CONFIG_FILE);
		} catch (IOException e) {
			System.err.println("Failed to create default configuration: " + e.getMessage());
		}
	}
	
	// Getter 메서드들
	public int getServerPort() {
		return getIntProperty(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
	}
	
	public int getCameraIndex() {
		return getIntProperty(KEY_CAMERA_INDEX, DEFAULT_CAMERA_INDEX);
	}
	
	public int getFrameRate() {
		return getIntProperty(KEY_FRAME_RATE, DEFAULT_FRAME_RATE);
	}
	
	public double getJpegQuality() {
		return getDoubleProperty(KEY_JPEG_QUALITY, DEFAULT_JPEG_QUALITY);
	}
	
	public String getServerHost() {
		return getStringProperty(KEY_SERVER_HOST, DEFAULT_SERVER_HOST);
	}
	
	public int getWindowWidth() {
		return getIntProperty(KEY_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH);
	}
	
	public int getWindowHeight() {
		return getIntProperty(KEY_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT);
	}
	
	public int getVideoWidth() {
		return getIntProperty(KEY_VIDEO_WIDTH, DEFAULT_VIDEO_WIDTH);
	}
	
	public int getVideoHeight() {
		return getIntProperty(KEY_VIDEO_HEIGHT, DEFAULT_VIDEO_HEIGHT);
	}
	
	public boolean getAutoStartServer() {
		return getBooleanProperty(KEY_AUTO_START_SERVER, false);
	}
	
	public boolean getAutoStartCamera() {
		return getBooleanProperty(KEY_AUTO_START_CAMERA, false);
	}
	
	public String getSnapshotsPath() {
		return getStringProperty(KEY_SAVE_SNAPSHOTS_PATH, "images");
	}
	
	public String getLogLevel() {
		return getStringProperty(KEY_LOG_LEVEL, "INFO");
	}
	
	// Setter 메서드들
	public void setServerPort(int port) {
		properties.setProperty(KEY_SERVER_PORT, String.valueOf(port));
	}
	
	public void setCameraIndex(int index) {
		properties.setProperty(KEY_CAMERA_INDEX, String.valueOf(index));
	}
	
	public void setFrameRate(int frameRate) {
		properties.setProperty(KEY_FRAME_RATE, String.valueOf(frameRate));
	}
	
	public void setJpegQuality(double quality) {
		properties.setProperty(KEY_JPEG_QUALITY, String.valueOf(quality));
	}
	
	public void setServerHost(String host) {
		properties.setProperty(KEY_SERVER_HOST, host);
	}
	
	public void setWindowSize(int width, int height) {
		properties.setProperty(KEY_WINDOW_WIDTH, String.valueOf(width));
		properties.setProperty(KEY_WINDOW_HEIGHT, String.valueOf(height));
	}
	
	public void setVideoSize(int width, int height) {
		properties.setProperty(KEY_VIDEO_WIDTH, String.valueOf(width));
		properties.setProperty(KEY_VIDEO_HEIGHT, String.valueOf(height));
	}
	
	public void setAutoStartServer(boolean autoStart) {
		properties.setProperty(KEY_AUTO_START_SERVER, String.valueOf(autoStart));
	}
	
	public void setAutoStartCamera(boolean autoStart) {
		properties.setProperty(KEY_AUTO_START_CAMERA, String.valueOf(autoStart));
	}
	
	public void setSnapshotsPath(String path) {
		properties.setProperty(KEY_SAVE_SNAPSHOTS_PATH, path);
	}
	
	public void setLogLevel(String level) {
		properties.setProperty(KEY_LOG_LEVEL, level);
	}
	
	// 유틸리티 메서드들
	private int getIntProperty(String key, int defaultValue) {
		try {
			return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
		} catch (NumberFormatException e) {
			System.err.println("Invalid integer value for " + key + ", using default: " + defaultValue);
			return defaultValue;
		}
	}
	
	private double getDoubleProperty(String key, double defaultValue) {
		try {
			return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
		} catch (NumberFormatException e) {
			System.err.println("Invalid double value for " + key + ", using default: " + defaultValue);
			return defaultValue;
		}
	}
	
	private boolean getBooleanProperty(String key, boolean defaultValue) {
		String value = properties.getProperty(key, String.valueOf(defaultValue));
		return Boolean.parseBoolean(value);
	}
	
	private String getStringProperty(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}
	
	/**
	 * 설정 유효성 검사
	 */
	public boolean validateConfig() {
		boolean valid = true;
		
		// 포트 번호 검사
		int port = getServerPort();
		if (port < 1024 || port > 65535) {
			System.err.println("Invalid server port: " + port + " (must be between 1024-65535)");
			valid = false;
		}
		
		// 프레임 레이트 검사
		int frameRate = getFrameRate();
		if (frameRate < 1 || frameRate > 60) {
			System.err.println("Invalid frame rate: " + frameRate + " (must be between 1-60)");
			valid = false;
		}
		
		// JPEG 품질 검사
		double quality = getJpegQuality();
		if (quality < 0.1 || quality > 1.0) {
			System.err.println("Invalid JPEG quality: " + quality + " (must be between 0.1-1.0)");
			valid = false;
		}
		
		return valid;
	}
	
	/**
	 * 설정 정보를 문자열로 반환
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ServerConfig {\n");
		sb.append("  Server Port: ").append(getServerPort()).append("\n");
		sb.append("  Camera Index: ").append(getCameraIndex()).append("\n");
		sb.append("  Frame Rate: ").append(getFrameRate()).append(" fps\n");
		sb.append("  JPEG Quality: ").append(getJpegQuality()).append("\n");
		sb.append("  Server Host: ").append(getServerHost()).append("\n");
		sb.append("  Window Size: ").append(getWindowWidth()).append("x").append(getWindowHeight()).append("\n");
		sb.append("  Video Size: ").append(getVideoWidth()).append("x").append(getVideoHeight()).append("\n");
		sb.append("  Auto Start Server: ").append(getAutoStartServer()).append("\n");
		sb.append("  Auto Start Camera: ").append(getAutoStartCamera()).append("\n");
		sb.append("  Snapshots Path: ").append(getSnapshotsPath()).append("\n");
		sb.append("  Log Level: ").append(getLogLevel()).append("\n");
		sb.append("}");
		return sb.toString();
	}
	
	/**
	 * 설정을 기본값으로 재설정
	 */
	public void resetToDefaults() {
		properties.clear();
		loadDefaults();
		System.out.println("Configuration reset to defaults");
	}
}