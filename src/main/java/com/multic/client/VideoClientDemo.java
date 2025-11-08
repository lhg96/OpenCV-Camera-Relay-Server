package com.multic.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * 비디오 클라이언트 데모 애플리케이션
 * - GUI를 통한 클라이언트 제어
 * - 실시간 통계 및 로그 표시
 * - 연결 상태 모니터링
 */
public class VideoClientDemo extends JFrame {
    private static final long serialVersionUID = 1L;
    
    // UI 컴포넌트
    private JTextField serverHostField;
    private JSpinner serverPortSpinner;
    private JSpinner cameraIndexSpinner;
    private JSpinner frameRateSpinner;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton statisticsButton;
    
    // 상태 표시
    private JLabel statusLabel;
    private JLabel connectionLabel;
    private JLabel framesLabel;
    private JLabel bytesLabel;
    private JTextArea logArea;
    
    // 비디오 클라이언트
    private VideoClient videoClient;
    
    // UI 업데이트 타이머
    private Timer updateTimer;

    public VideoClientDemo() {
        initializeUI();
        setupEventListeners();
        startUpdateTimer();
    }

    private void initializeUI() {
        setTitle("Video Client - 비디오 클라이언트");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 설정 패널
        JPanel configPanel = createConfigPanel();
        add(configPanel, BorderLayout.NORTH);

        // 상태 패널
        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.CENTER);

        // 로그 패널
        JPanel logPanel = createLogPanel();
        add(logPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        
        // 초기 상태 설정
        updateButtonStates(false);
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 5, 5));
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("서버 연결 설정"));

        // 서버 설정
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverPanel.add(new JLabel("서버 주소:"));
        serverHostField = new JTextField("localhost", 10);
        serverPanel.add(serverHostField);
        
        serverPanel.add(new JLabel("포트:"));
        serverPortSpinner = new JSpinner(new SpinnerNumberModel(8080, 1024, 65535, 1));
        serverPortSpinner.setPreferredSize(new java.awt.Dimension(80, 25));
        serverPanel.add(serverPortSpinner);
        
        panel.add(serverPanel);

        // 카메라 설정
        JPanel cameraPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cameraPanel.add(new JLabel("카메라 번호:"));
        cameraIndexSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        cameraIndexSpinner.setPreferredSize(new java.awt.Dimension(60, 25));
        cameraPanel.add(cameraIndexSpinner);
        
        cameraPanel.add(new JLabel("프레임 레이트:"));
        frameRateSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 60, 1));
        frameRateSpinner.setPreferredSize(new java.awt.Dimension(60, 25));
        cameraPanel.add(frameRateSpinner);
        
        panel.add(cameraPanel);

        // 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout());
        connectButton = new JButton("서버에 연결");
        disconnectButton = new JButton("연결 끊기");
        statisticsButton = new JButton("통계 보기");
        
        buttonPanel.add(connectButton);
        buttonPanel.add(disconnectButton);
        buttonPanel.add(statisticsButton);
        
        panel.add(buttonPanel);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 1, 5, 5));
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("클라이언트 상태"));

        statusLabel = new JLabel("상태: 연결 안됨");
        connectionLabel = new JLabel("서버 연결: 없음");
        framesLabel = new JLabel("전송한 프레임: 0");
        bytesLabel = new JLabel("전송한 데이터: 0");

        panel.add(statusLabel);
        panel.add(connectionLabel);
        panel.add(framesLabel);
        panel.add(bytesLabel);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("클라이언트 로그"));

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void setupEventListeners() {
        // Connect 버튼
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToServer();
            }
        });

        // Disconnect 버튼
        disconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnectFromServer();
            }
        });

        // Statistics 버튼
        statisticsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showStatistics();
            }
        });

        // 윈도우 닫기 이벤트
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (videoClient != null && videoClient.isRunning()) {
                    videoClient.stop();
                }
                if (updateTimer != null && updateTimer.isRunning()) {
                    updateTimer.stop();
                }
                System.exit(0);
            }
        });
    }

    private void connectToServer() {
        try {
            String host = serverHostField.getText().trim();
            int port = (Integer) serverPortSpinner.getValue();
            int cameraIndex = (Integer) cameraIndexSpinner.getValue();
            double frameRate = (Integer) frameRateSpinner.getValue();

            if (host.isEmpty()) {
                appendLog("오류: 서버 주소를 입력해주세요");
                return;
            }

            appendLog("서버에 연결 중: " + host + ":" + port + " (카메라 " + cameraIndex + ")");

            // 비디오 클라이언트 생성
            videoClient = new VideoClient(host, port, cameraIndex, frameRate, 0.8);
            
            // 이벤트 리스너 설정
            videoClient.setEventListener(new VideoClient.ClientEventListener() {
                @Override
                public void onConnected() {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("서버에 연결되었습니다");
                        updateButtonStates(true);
                    });
                }

                @Override
                public void onDisconnected() {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("서버 연결이 끊어졌습니다");
                        updateButtonStates(false);
                    });
                }

                @Override
                public void onFrameSent(long frameNumber, int frameSize) {
                    // UI 업데이트는 타이머에서 처리 (너무 빈번함)
                }

                @Override
                public void onError(Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("오류: " + e.getMessage());
                    });
                }

                @Override
                public void onCameraOpened(int width, int height) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("카메라 열림: " + width + "x" + height);
                    });
                }

                @Override
                public void onCameraClosed() {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("카메라 닫힘");
                    });
                }
            });

            // 클라이언트 시작
            if (videoClient.start()) {
                appendLog("비디오 클라이언트 시작됨");
            } else {
                appendLog("오류: 비디오 클라이언트 시작 실패");
                videoClient = null;
            }

        } catch (Exception e) {
            appendLog("오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void disconnectFromServer() {
        if (videoClient != null) {
            appendLog("서버 연결을 끊는 중...");
            videoClient.stop();
            videoClient = null;
            updateButtonStates(false);
        }
    }

    private void showStatistics() {
        if (videoClient != null) {
            videoClient.printStatistics();
            appendLog("통계가 콘솔에 출력되었습니다");
        } else {
            appendLog("활성 연결이 없습니다");
        }
    }

    private void updateButtonStates(boolean connected) {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        statisticsButton.setEnabled(connected);
        
        serverHostField.setEnabled(!connected);
        serverPortSpinner.setEnabled(!connected);
        cameraIndexSpinner.setEnabled(!connected);
        frameRateSpinner.setEnabled(!connected);
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logEntry = "[" + timestamp + "] " + message + "\n";
            
            logArea.append(logEntry);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateStatus();
            }
        });
        updateTimer.start();
    }

    private void updateStatus() {
        if (videoClient != null && videoClient.isRunning()) {
            statusLabel.setText("상태: 연결됨");
            connectionLabel.setText("연결: " + videoClient.getServerHost() + ":" + videoClient.getServerPort());
            framesLabel.setText("전송된 프레임: " + videoClient.getFramesSent());
            
            long bytes = videoClient.getBytesSent();
            String bytesStr = String.format("%.2f KB", bytes / 1024.0);
            bytesLabel.setText("전송된 바이트: " + bytesStr);
            
        } else {
            statusLabel.setText("상태: 연결 끊어짐");
            connectionLabel.setText("연결: 없음");
            framesLabel.setText("전송된 프레임: 0");
            bytesLabel.setText("전송된 바이트: 0");
        }
    }

    public static void main(String[] args) {
        // macOS에서 카메라 권한 우회 설정
        System.setProperty("OPENCV_AVFOUNDATION_SKIP_AUTH", "1");
        
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            new VideoClientDemo().setVisible(true);
        });
    }
}