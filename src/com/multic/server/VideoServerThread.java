package com.multic.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * refer
 * https://stackoverflow.com/questions/43276792/tcp-webcam-streaming-in-java
 */
public class VideoServerThread extends Thread {
	private ServerSocket 	serverSocket;
	private int 			videoServerPort;
	private Socket 			socket;
	private JPanel 			panel;
	private boolean 		calling;

	public VideoServerThread(ServerSocket serverSocket, int videoServerPort, JPanel panel, boolean calling) {
		this.serverSocket = serverSocket;
		this.videoServerPort = videoServerPort;
		this.panel = panel;
		this.calling = calling;
	}

	@Override
	public void run() {
		System.out.println("Video Server opened!");
		try {
			serverSocket = new ServerSocket(videoServerPort);
			socket = serverSocket.accept();
			InputStream in = socket.getInputStream();
			ObjectInputStream ois = new ObjectInputStream(in);
			BufferedImage bufferedImage;
			InputStream inputImage;
			Frame f;
			while (calling) {
				f = (Frame) ois.readObject();
				inputImage = new ByteArrayInputStream(f.bytes);
				bufferedImage = ImageIO.read(inputImage);
				panel.getGraphics().drawImage(bufferedImage, 0, 0, panel.getWidth(), panel.getHeight(), null);
				panel.getGraphics().drawImage(bufferedImage, 0, 0, null);
				bufferedImage.flush();
				inputImage.close();
				f = null;
			}

		}

		catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	class Frame implements Serializable {
		public byte[] bytes;

		public Frame(byte[] bytes) {
			this.bytes = bytes;
		}

		public int size() {
			return bytes.length;
		}
	}
}