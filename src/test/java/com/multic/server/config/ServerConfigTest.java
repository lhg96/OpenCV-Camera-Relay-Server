package com.multic.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.multic.server.config.ServerConfig;

/**
 * ServerConfig 클래스에 대한 단위 테스트
 * - 설정 로드/저장 테스트
 * - 기본값 검증 테스트
 * - 설정 유효성 검사 테스트
 * - 파일 I/O 오류 처리 테스트
 */
class ServerConfigTest {

    private ServerConfig config;
    private File testConfigFile;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // 임시 설정 파일 생성
        testConfigFile = tempDir.resolve("test-server.properties").toFile();
        config = new ServerConfig();
        
        // 원래 설정 파일이 있으면 백업
        File originalConfig = new File("server.properties");
        if (originalConfig.exists()) {
            Files.copy(originalConfig.toPath(), 
                      tempDir.resolve("backup-server.properties"));
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // 테스트 파일 정리
        if (testConfigFile != null && testConfigFile.exists()) {
            testConfigFile.delete();
        }
        
        // 원본 설정 파일이 있으면 복원
        File backupConfig = tempDir.resolve("backup-server.properties").toFile();
        File originalConfig = new File("server.properties");
        if (backupConfig.exists() && originalConfig.exists()) {
            Files.copy(backupConfig.toPath(), originalConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    @DisplayName("기본값이 정확하게 설정되어야 함")
    void defaultValuesTest() {
        // Given & When
        ServerConfig defaultConfig = new ServerConfig();
        
        // Then
        assertEquals(ServerConfig.DEFAULT_SERVER_PORT, defaultConfig.getServerPort(),
            "기본 서버 포트가 정확해야 함");
        assertEquals(ServerConfig.DEFAULT_CAMERA_INDEX, defaultConfig.getCameraIndex(),
            "기본 카메라 인덱스가 정확해야 함");
        assertEquals(ServerConfig.DEFAULT_FRAME_RATE, defaultConfig.getFrameRate(),
            "기본 프레임 레이트가 정확해야 함");
        assertEquals(ServerConfig.DEFAULT_JPEG_QUALITY, defaultConfig.getJpegQuality(), 0.001,
            "기본 JPEG 품질이 정확해야 함");
        assertEquals(ServerConfig.DEFAULT_SERVER_HOST, defaultConfig.getServerHost(),
            "기본 서버 호스트가 정확해야 함");
        assertEquals(ServerConfig.DEFAULT_WINDOW_WIDTH, defaultConfig.getWindowWidth(),
            "기본 윈도우 너비가 정확해야 함");
        assertEquals(ServerConfig.DEFAULT_WINDOW_HEIGHT, defaultConfig.getWindowHeight(),
            "기본 윈도우 높이가 정확해야 함");
        assertFalse(defaultConfig.getAutoStartServer(),
            "기본적으로 자동 시작 서버가 비활성화되어야 함");
        assertFalse(defaultConfig.getAutoStartCamera(),
            "기본적으로 자동 시작 카메라가 비활성화되어야 함");
    }

    @Test
    @DisplayName("설정 값 변경이 정상적으로 이루어져야 함")
    void setterMethodsTest() {
        // Given
        int newPort = 8080;
        int newCameraIndex = 1;
        int newFrameRate = 25;
        double newQuality = 0.9;
        String newHost = "192.168.1.100";
        
        // When
        config.setServerPort(newPort);
        config.setCameraIndex(newCameraIndex);
        config.setFrameRate(newFrameRate);
        config.setJpegQuality(newQuality);
        config.setServerHost(newHost);
        config.setAutoStartServer(true);
        config.setAutoStartCamera(true);
        
        // Then
        assertEquals(newPort, config.getServerPort(), "서버 포트가 변경되어야 함");
        assertEquals(newCameraIndex, config.getCameraIndex(), "카메라 인덱스가 변경되어야 함");
        assertEquals(newFrameRate, config.getFrameRate(), "프레임 레이트가 변경되어야 함");
        assertEquals(newQuality, config.getJpegQuality(), 0.001, "JPEG 품질이 변경되어야 함");
        assertEquals(newHost, config.getServerHost(), "서버 호스트가 변경되어야 함");
        assertTrue(config.getAutoStartServer(), "자동 시작 서버가 활성화되어야 함");
        assertTrue(config.getAutoStartCamera(), "자동 시작 카메라가 활성화되어야 함");
    }

    @Test
    @DisplayName("윈도우 및 비디오 크기 설정이 정상적으로 이루어져야 함")
    void windowAndVideoSizeTest() {
        // Given
        int windowWidth = 1200;
        int windowHeight = 800;
        int videoWidth = 800;
        int videoHeight = 600;
        
        // When
        config.setWindowSize(windowWidth, windowHeight);
        config.setVideoSize(videoWidth, videoHeight);
        
        // Then
        assertEquals(windowWidth, config.getWindowWidth(), "윈도우 너비가 설정되어야 함");
        assertEquals(windowHeight, config.getWindowHeight(), "윈도우 높이가 설정되어야 함");
        assertEquals(videoWidth, config.getVideoWidth(), "비디오 너비가 설정되어야 함");
        assertEquals(videoHeight, config.getVideoHeight(), "비디오 높이가 설정되어야 함");
    }

    @Test
    @DisplayName("설정 파일 저장 및 로드가 정상적으로 이루어져야 함")
    @Timeout(5)
    void saveAndLoadConfigTest() throws IOException {
        // Given
        int testPort = 9090;
        String testHost = "test.example.com";
        double testQuality = 0.75;
        
        config.setServerPort(testPort);
        config.setServerHost(testHost);
        config.setJpegQuality(testQuality);
        config.setAutoStartServer(true);
        
        // When - 저장
        config.saveToFile();
        
        // 새 인스턴스로 로드
        ServerConfig loadedConfig = new ServerConfig();
        
        // Then
        assertEquals(testPort, loadedConfig.getServerPort(), "저장된 포트가 로드되어야 함");
        assertEquals(testHost, loadedConfig.getServerHost(), "저장된 호스트가 로드되어야 함");
        assertEquals(testQuality, loadedConfig.getJpegQuality(), 0.001, 
            "저장된 품질이 로드되어야 함");
        assertTrue(loadedConfig.getAutoStartServer(), "저장된 자동 시작 설정이 로드되어야 함");
    }

    @Test
    @DisplayName("설정 유효성 검사가 정확하게 이루어져야 함")
    void configValidationTest() {
        // Given - 유효한 설정
        config.setServerPort(8080);
        config.setFrameRate(30);
        config.setJpegQuality(0.8);
        
        // When & Then
        assertTrue(config.validateConfig(), "유효한 설정은 검증을 통과해야 함");
        
        // Given - 무효한 포트
        config.setServerPort(100); // 1024 미만
        
        // When & Then
        assertFalse(config.validateConfig(), "무효한 포트는 검증을 실패해야 함");
        
        // Given - 무효한 프레임 레이트
        config.setServerPort(8080); // 유효한 포트로 복원
        config.setFrameRate(100); // 60 초과
        
        // When & Then
        assertFalse(config.validateConfig(), "무효한 프레임 레이트는 검증을 실패해야 함");
        
        // Given - 무효한 JPEG 품질
        config.setFrameRate(30); // 유효한 값으로 복원
        config.setJpegQuality(1.5); // 1.0 초과
        
        // When & Then
        assertFalse(config.validateConfig(), "무효한 JPEG 품질은 검증을 실패해야 함");
    }

    @Test
    @DisplayName("기본 설정 파일 생성이 정상적으로 이루어져야 함")
    void createDefaultConfigFileTest() {
        // Given & When
        config.createDefaultConfigFile();
        
        // Then
        File defaultFile = new File("server-default.properties");
        assertTrue(defaultFile.exists(), "기본 설정 파일이 생성되어야 함");
        
        // 정리
        defaultFile.delete();
    }

    @Test
    @DisplayName("설정을 기본값으로 재설정할 수 있어야 함")
    void resetToDefaultsTest() {
        // Given - 설정 변경
        config.setServerPort(9999);
        config.setFrameRate(15);
        config.setAutoStartServer(true);
        
        // When
        config.resetToDefaults();
        
        // Then
        assertEquals(ServerConfig.DEFAULT_SERVER_PORT, config.getServerPort(),
            "포트가 기본값으로 재설정되어야 함");
        assertEquals(ServerConfig.DEFAULT_FRAME_RATE, config.getFrameRate(),
            "프레임 레이트가 기본값으로 재설정되어야 함");
        assertFalse(config.getAutoStartServer(),
            "자동 시작 서버가 기본값으로 재설정되어야 함");
    }

    @Test
    @DisplayName("toString 메서드가 모든 설정 정보를 포함해야 함")
    void toStringTest() {
        // Given
        config.setServerPort(8080);
        config.setServerHost("localhost");
        
        // When
        String configString = config.toString();
        
        // Then
        assertNotNull(configString, "toString 결과가 null이면 안됨");
        assertTrue(configString.contains("8080"), "포트 정보가 포함되어야 함");
        assertTrue(configString.contains("localhost"), "호스트 정보가 포함되어야 함");
        assertTrue(configString.contains("ServerConfig"), "클래스 이름이 포함되어야 함");
    }

    @Test
    @DisplayName("잘못된 설정 값 로드 시 기본값을 사용해야 함")
    void invalidConfigValueHandlingTest() throws IOException {
        // Given - 잘못된 설정 파일 생성
        Properties badProps = new Properties();
        badProps.setProperty("server.port", "invalid_number");
        badProps.setProperty("camera.frameRate", "not_a_number");
        badProps.setProperty("camera.jpegQuality", "invalid_quality");
        
        try (FileOutputStream fos = new FileOutputStream(testConfigFile)) {
            badProps.store(fos, "Bad configuration for testing");
        }
        
        // When - 잘못된 설정 파일이 있는 상태에서 로드
        System.setProperty("user.dir", tempDir.toString());
        ServerConfig badConfig = new ServerConfig();
        
        // Then - 기본값이 사용되어야 함
        assertEquals(ServerConfig.DEFAULT_SERVER_PORT, badConfig.getServerPort(),
            "잘못된 포트 값일 때 기본값을 사용해야 함");
        assertEquals(ServerConfig.DEFAULT_FRAME_RATE, badConfig.getFrameRate(),
            "잘못된 프레임 레이트일 때 기본값을 사용해야 함");
        assertEquals(ServerConfig.DEFAULT_JPEG_QUALITY, badConfig.getJpegQuality(), 0.001,
            "잘못된 품질 값일 때 기본값을 사용해야 함");
    }

    @Test
    @DisplayName("스냅샷 경로 설정이 정상적으로 이루어져야 함")
    void snapshotsPathTest() {
        // Given
        String customPath = "/custom/snapshots/path";
        
        // When
        config.setSnapshotsPath(customPath);
        
        // Then
        assertEquals(customPath, config.getSnapshotsPath(),
            "스냅샷 경로가 정확히 설정되어야 함");
    }

    @Test
    @DisplayName("로그 레벨 설정이 정상적으로 이루어져야 함")
    void logLevelTest() {
        // Given
        String debugLevel = "DEBUG";
        
        // When
        config.setLogLevel(debugLevel);
        
        // Then
        assertEquals(debugLevel, config.getLogLevel(),
            "로그 레벨이 정확히 설정되어야 함");
    }
}