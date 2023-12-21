package com.multic.camera;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

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
	
	private boolean clicked = false;
	
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
				clicked =true;
			}
		});
		
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				// TODO Auto-generated method stub
				super.windowClosing(e);
				capture.release();
				image.release();
				System.exit(0);
			}		
			
		});
		
		
		setSize(new Dimension(640,  560));
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
	}
	
	//create camera
	public void startCamera() {
		capture = new VideoCapture(0);
		image = new Mat();
		byte[] imageData;
		
		ImageIcon icon;		
		
		//human recognize
		//https://www.youtube.com/watch?v=p-ooOHkJLFY&list=PLsjTcuj_fDEYXKcZ1KCZWILnVsQDFJZrn&index=5
		
		
		while(true) {
			capture.read(image);
			
			//convert matrix to byte
			final MatOfByte buf = new MatOfByte();
			Imgcodecs.imencode(".jpg", image, buf);
			imageData = buf.toArray();
			
			//add to JLabel
			icon = new ImageIcon(imageData);
			cameraScreen.setIcon(icon);
			
			//capture and save to file
			if(clicked) {
				String name = JOptionPane.showInputDialog("Enter Image Name");
				if(name == null) {
					name = new SimpleDateFormat("yyyy-mm-dd-hh-mm-ss").format(new Date());
				}
				Imgcodecs.imwrite("images/"+name+".jpg", image);
				
				clicked = false;
			}
			
		}
		
	}
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		System.out.println("load success opencv");
		EventQueue.invokeLater(new Runnable() {
			
			@Override
			public void run() {
				OpenCVCameraDemo camera = new OpenCVCameraDemo();
				new Thread(new Runnable() {
					public void run() {
						camera.startCamera();
					}
				}).start();;
			}
		});
	}
}
