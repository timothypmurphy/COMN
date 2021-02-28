/* Stephen McGruer 0840449 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * The Receiver3 class represents a receiving of a set of bytes via
 * the UDP protocol, using a go-back-N policy. After each received
 * packet, an acknowledgement packet (ack) is sent back confirming the
 * packet's arrival. Duplicate or out of order packets are discarded.
 * <p>
 * The data packet protocol used by this class is as follows:
 * <ul>
 * <li>The first two bytes are the packet number (giving a value range of 0 to 65536).
 * <li>The next byte is the EOF byte. A value of anything but 0 signifies an EOF packet.
 * <li>The remaining (<a href="#PACKET_SIZE">PACKET_SIZE</a> - 3) bytes are filled with data.
 * </ul>
 * <p>
 * The ack packet is merely the packet number (and thus is 2 bytes).
 * <p>
 * Note that the ack-sending port is hard-coded to be the incoming port + 1, as the
 * coursework does not specify any way of setting when calling the program.
 * 
 * @author s0840449
 *
 */
public class Receiver3 {

	/** Defines the size of a packet, in bytes. Usually 1024, must be at least 4 or no
	 * data will be transferred. */
	public static final int PACKET_SIZE = 1024;
	
	/* Using a constant value for DEBUG_MODE allows the compiler to
	 * optimise away the if statements that rely on it and thus remove
	 * all performance hits when debugging is off. */
	private static final boolean DEBUG_MODE = false;

	/* The file name to write to. */
	private String fileName;

	/* The port number to listen for data on. */
	private int portNumber;

	/* The incoming and outgoing sockets. */
	private DatagramSocket receiverSocket;
	private DatagramSocket ackSocket;
	
	/**
	 * Default constructor. 
	 * 
	 * @param fileName		The output file name to write to.
	 * @param portNumber	The port number to wait for data on.
	 */
	public Receiver3(String fileName, int portNumber) {
		this.fileName = fileName;
		this.portNumber = portNumber;
	}
	
	/**
	 * Receives data from the set port and writes the received data to a file.
	 * Operates on a go-back-N protocol. When a correct packet is received, 
	 * an ack is sent back for it. If an incorrect packet is received (either
	 * a duplicate or out of order packet) an ack is sent for the last
	 * correctly received packet.  Duplicate/out-of-order packets are discarded
	 * rather than buffered.
	 * 
	 * @return		True if data is correctly received and the output file 
	 * 				written, False otherwise. A value of True does <b>not</b>
	 * 				guarantee that the received data is correct other than that
	 * 				all packets were received in the correct order. The original
	 * 				data may be incorrect or may have been corrupted enroute.
	 */
	public boolean receive() {
				
		/* Tracks whether we have received all packets. */
		boolean finishedTransfer = false;
		
		/* If debug mode is set, tracks the number of data bytes that we receive. */
		int numReceivedFileBytes = 0;
		
		/* Used to check for duplicate/out-of-order packets. */
		int prevPacketNum = 0;

		try {
			
			/* We use a FileOutputStream to write the data we receive to
			 * a file. */
			FileOutputStream writer = new FileOutputStream(new File(this.fileName));

			receiverSocket = new DatagramSocket(portNumber);
			ackSocket = new DatagramSocket();

			/* Grab packets until we receive an (expected) EOF packet. */
			while (!finishedTransfer) {
				
				/* Buffer for the incoming data. */
				byte receivedDataBuffer[] = new byte[PACKET_SIZE];

				/* Grab an incoming packet (block waiting for one). */
				DatagramPacket receivedPacket = new DatagramPacket(receivedDataBuffer, receivedDataBuffer.length);
				receiverSocket.receive(receivedPacket);
				
				byte receivedData[] = receivedPacket.getData();
				
				/* The current packet's size is not necessarily PACKET_SIZE - the
				 * final packet may contain less data. */
				int currentPacketSize = receivedPacket.getLength();

				/* Must take care to avoid int-promotion errors. */
				int packetNum = (0x0000FF00 & (receivedData[0] << 8)) | (0x000000FF & receivedData[1]);
						
				/* Only grab the data if this is the expected packet. */
				if (packetNum == (prevPacketNum + 1)) {
	
					/* EOF check. */
					if (receivedData[2] > 0) {
						finishedTransfer = true;
					}
	
					/* Write the file data from the packet. */
					writer.write(receivedData, 3, currentPacketSize - 3);
					
					if (DEBUG_MODE) {
						System.out.println("DEBUG: Received packet " + packetNum + " with size " +
								currentPacketSize + " and data size " + (currentPacketSize - 3));
						numReceivedFileBytes += currentPacketSize - 3;
					}
					
					prevPacketNum++;
					
				} else if (DEBUG_MODE) {
					System.out.println("DEBUG: Duplicate/out-of-order packet " + packetNum + " received and discarded. " +
							"Expected packet was " + (prevPacketNum + 1));
				}
					
				/* Send the ack packet for the last good packet number back to the sender. */
				InetAddress ipAddress = receivedPacket.getAddress();
				
				byte[] ackBuffer = new byte[2];
				ackBuffer[1] = (byte) (prevPacketNum >>> 8);
				ackBuffer[0] = (byte) prevPacketNum;
				
				DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length,
						ipAddress, portNumber + 1);
				ackSocket.send(ackPacket);
				
				if (DEBUG_MODE) {
					System.out.println("DEBUG: Sent ack with packet num " + prevPacketNum + ".");
				}
				
			}
			
			if (DEBUG_MODE) {
				System.out.println("DEBUG: Finished receiving packets.");
				System.out.println("DEBUG: Recieved file size is " + numReceivedFileBytes + " bytes.");
			}
		
		} catch (SocketException se) {
			
			System.err.println("Error: Unable to open a datagram socket:");
			System.err.println(se.getMessage());
			return false;
			
		} catch (IOException ioe) {
			
			System.err.println("Error: IO Exception:");
			System.err.println(ioe.getMessage());
			return false;
			
		} finally {
			
			receiverSocket.close();
			ackSocket.close();
			
		}
		
		return true;
		
	}
	
	/**
	 * The main method for running the Receiver3 class. There are two arguments -
	 * the port number to receive on and the name of the file to write.
	 * 
	 * @param args		The program arguments: the port number and the 
	 * 					name of the file that is to be sent.
	 */
	public static void main(String[] args) {
		
		int portNumber;
		String fileName = null;
		boolean successful = false;
		
		/* Both arguments are compulsory. */
		if (args.length != 2) {
			System.err.println("Usage: java Receiver3 port filename");
			System.exit(-1);
		}
		
		try {
			
			portNumber = Integer.parseInt(args[0]);
			fileName = args[1];
	
			/* Attempt to receive data and write it to the given file. */
			Receiver3 receiver = new Receiver3(fileName, portNumber);
			successful = receiver.receive();
			
		} catch (NumberFormatException nfe) {
			System.err.println("Error: Unable to convert input port number \""
					+ args[0] + "\" to an integer.");
			System.exit(-1);
		}
		
		if (successful) {
			System.out.println("Success! A file has been received and written to " + fileName);
		} else {
			System.err.println("File was not received successfully. Please try again.");
		}
		
	}

}
