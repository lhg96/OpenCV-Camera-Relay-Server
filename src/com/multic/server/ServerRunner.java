package com.multic.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;

public class ServerRunner {

	public static void main(String[] args) {
		String host = "127.0.0.1";
	    int port 	= 5252;
	    byte buf[] 	= new byte[2]; 
	    byte send[] = { 13, 18 };

	    //Server
	    //VideoServerThread server = new VideoServerThread(new ServerSocket(port), , true)
	    
	    
	    //DatagramSocket serverSocket  = new DatagramSocket(port);  
	    //DatagramPacket receivePacket = new DatagramPacket(buf, 2); 
	    //serverSocket.receive(receivePacket); 

	    //Client
	    //DatagramSocket clientSocket  = new DatagramSocket(host, port); 
	    //DatagramPacket sendPacket = new DatagramPacket(send, 2, clientSocket.getAddress(), clientSocket.getPort()); 
	    //clientSocket.send(sendPacket); 

	}

}
