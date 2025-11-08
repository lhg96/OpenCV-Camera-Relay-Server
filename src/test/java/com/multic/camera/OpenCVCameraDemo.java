package com.multic.camera;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import nu.pattern.OpenCV;

/**
 * refer 
 * https://www.youtube.com/watch?v=NUQc7-dYIxA&list=PLsjTcuj_fDEYXKcZ1KCZWILnVsQDFJZrn
 * 
 * @author hyun
 *
 */
public class OpenCVCameraDemo extends JFrame{
	
	private JLabel cameraScreen;
	private JButton btnCapture;
	
	private VideoCapture capture;
	private Mat image;
	private Thread cameraWorker;
	
	private final AtomicBoolean captureRequested = new AtomicBoolean(false);
	private volatile boolean running;
	
	public OpenCVCameraDemo() {
		setLayout(null);
		
		cameraScreen = new JLabel();
		cameraScreen.setBounds(0, 0, 640 , 480);
		add(cameraScreen);
		
		btnCapture = new JButton("Capture");
		btnCapture.setBounds(300, 480,80,40);
		add(btnCapture);
		
		btnCapture.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				captureRequested.set(true);
			}
		});
		
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				stopCamera();
			}		
			
		});
		
		
		setSize(new Dimension(640,  560));
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
	}
	
	public void startCameraAsync() {
		if (cameraWorker != null && cameraWorker.isAlive()) {
			return;
		}
		running = true;
		cameraWorker = new Thread(this::captureLoop, "OpenCVCameraLoop");
		cameraWorker.start();
	}

	private void captureLoop() {
		capture = new VideoCapture(0);
		if (!capture.isOpened()) {
			showError("카메라를 열 수 없습니다. 장치 연결을 확인하세요.");
			running = false;
			releaseResources();
			return;
		}

		image = new Mat();
		final MatOfByte buffer = new MatOfByte();

		try {
			while (running) {
				if (!capture.read(image)) {
					continue;
				}

				Imgcodecs.imencode(".jpg", image, buffer);
				final byte[] imageData = buffer.toArray();

				SwingUtilities.invokeLater(() -> cameraScreen.setIcon(new ImageIcon(imageData)));

				if (captureRequested.compareAndSet(true, false)) {
					saveFrame(image);
				}
			}
		} finally {
			buffer.release();
			releaseResources();
		}
	}

	private void saveFrame(Mat frame) {
		Mat snapshot = frame.clone();
		try {
			String name = requestSnapshotName();
			Path imagesDir = Paths.get("images");
			Files.createDirectories(imagesDir);
			String filePath = imagesDir.resolve(name + ".jpg").toString();
			if (!Imgcodecs.imwrite(filePath, snapshot)) {
				showError("이미지를 저장하지 못했습니다: " + filePath);
			}
		} catch (Exception e) {
			showError("이미지 저장 중 오류가 발생했습니다: " + e.getMessage());
		} finally {
			snapshot.release();
		}
	}

	private String requestSnapshotName() {
		final String[] nameHolder = new String[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				String name = JOptionPane.showInputDialog(this, "Enter Image Name");
				if (name == null || name.trim().isEmpty()) {
					nameHolder[0] = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
				} else {
					nameHolder[0] = name.trim();
				}
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			nameHolder[0] = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
		} catch (InvocationTargetException e) {
			nameHolder[0] = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
		}
		return nameHolder[0];
	}

	private void stopCamera() {
		running = false;
		captureRequested.set(false);
		if (cameraWorker != null && cameraWorker.isAlive() && Thread.currentThread() != cameraWorker) {
			try {
				cameraWorker.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		releaseResources();
		cameraWorker = null;
	}

	private void releaseResources() {
		if (capture != null) {
			capture.release();
			capture = null;
		}
		if (image != null) {
			image.release();
			image = null;
		}
	}

	private void showError(String message) {
		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Camera Error", JOptionPane.ERROR_MESSAGE));
	}

	public static void main(String[] args) {
		try {
			OpenCV.loadShared();
			System.out.println("Load success OpenCV (nu.pattern)");
		} catch (UnsatisfiedLinkError e) {
			System.err.println("OpenCV native library를 로드할 수 없습니다: " + e.getMessage());
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			
			@Override
			public void run() {
				OpenCVCameraDemo camera = new OpenCVCameraDemo();
				camera.startCameraAsync();
			}
		});
	}
}