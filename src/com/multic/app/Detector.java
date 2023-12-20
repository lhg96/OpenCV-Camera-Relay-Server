package com.multic.app;

import java.awt.EventQueue;

import org.opencv.core.Core;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/**
 * model file https://github.com/opencv/opencv/tree/master/data/haarcascades
 * https://github.com/opencv-java/face-detection/tree/master/resources/lbpcascades
 * 
 * refer
 * https://luvstudy.tistory.com/171
 * 
 * @author hyun
 *
 */
public class Detector {
	
	public Mat detectFace(Mat image) {
		// Instantiating the CascadeClassifier
		String xmlFile = "model/lbpcascade_frontalface.xml";
		CascadeClassifier classifier = new CascadeClassifier(xmlFile);

		// Detecting the face in the snap
		MatOfRect faceDetections = new MatOfRect();
		classifier.detectMultiScale(image, faceDetections);
		System.out.println(String.format("Detected %s faces", faceDetections.toArray().length));

		// Drawing boxes
		for (Rect rect : faceDetections.toArray()) {
			Imgproc.rectangle(image, // where to draw the box
					new Point(rect.x, rect.y), // bottom left
					new Point(rect.x + rect.width, rect.y + rect.height), // top right
					new Scalar(0, 0, 255), 3 // RGB colour
			);
		}
		return image;
	}

	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		System.out.println("load success opencv");

		// Reading the Image from the file and storing it in to a Matrix object
		String faceFile = "images/aaa.jpg";
		Mat image = Imgcodecs.imread(faceFile);

		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				Detector detector = new Detector();
				new Thread(new Runnable() {
					public void run() {
						Mat resultImage = detector.detectFace(image);
						// Writing the image
						Imgcodecs.imwrite("images/output1.jpg", resultImage);
						System.out.println("Image Processed");
					}
				}).start();
				;
			}
		});

	}
}
