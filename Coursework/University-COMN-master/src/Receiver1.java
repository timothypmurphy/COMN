/* Stephen McGruer 0840449 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * The Receiver1 class represents a naive receiving of a set of bytes via
 * the UDP protocol. It does check for incorrect package order based on
 * the packet number, but does not attempt to correct for missing packets.
 * <p>
 * The packet protocol used by this class is as follows:
 * <ul>
 * <li>The first two bytes are the packet number (giving a value range of 0 to 65536).
 * <li>The next byte is the EOF byte. A value of anything but 0 signifies an EOF packet.
 * <li>The remaining (<a href="#PACKET_SIZE">PACKET_SIZE</a> - 3) bytes are filled with data.
 * </ul>
 * 
 * @author s0840449
 *
 */
public class Receiver1 {

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
	
	/* The incoming socket. */
	private DatagramSocket receiverSocket;
	
	/**
	 * Default constructor. 
	 * 
	 * @param fileName		The name of the output file.
	 * @param portNumber	The port number to listen for data on.
	 */
	public Receiver1(String fileName, int portNumber) {
		this.portNumber = portNumber;
		this.fileName = fileName;
	}
	
	/**
	 * Receives data from the set port and writes the received data to a file.
	 * 
	 * @return		True if data is correctly received and the output file 
	 * 				written, False otherwise. A value of True does <b>not</b>
	 * 				guarantee that the received data is correct other than that
	 * 				an end of file packet was received. The original data may 
	 * 				be incorrect or may have been corrupted enroute.
	 */
	public boolean receive() {
				
		/* Tracks whether we have finished receiving data (i.e. an EOF
		 * packet has been received). */
		boolean finishedTransfer = false;
		
		/* If debug mode is set, tracks the number of data bytes that we receive. */
		int numReceivedFileBytes = 0;
		
		try {

			/* We use a FileOutputStream to write the data we receive to
			 * a file. */
			FileOutputStream writer = new FileOutputStream(new File(this.fileName));
			
			receiverSocket = new DatagramSocket(portNumber);

			/* Grab packets until we receive an EOF packet. */
			while (!finishedTransfer) {

				/* Buffer for the incoming data. */
				byte receivedDataBuffer[] = new byte[PACKET_SIZE];

				/* Grab an incoming packet (block waiting for one). */
				DatagramPacket packet = new DatagramPacket(receivedDataBuffer, receivedDataBuffer.length);
				receiverSocket.receive(packet);
				
				byte data[] = packet.getData();
				
				/* The current packet's size is not necessarily PACKET_SIZE - the
				 * final packet may contain less data. */
				int currentPacketSize = packet.getLength();

				/* Must take care to avoid int-promotion errors. */
				int packetNum = (0x0000FF00 & (data[0] << 8)) | (0x000000FF & data[1]);

				/* EOF check. */
				if (data[2] > 0) {
					finishedTransfer = true;
				}

				/* Write the file data from the packet. */
				writer.write(data, 3, currentPacketSize - 3);
				
				if (DEBUG_MODE) {
					System.out.println("DEBUG: Received packet " + packetNum + " with size " +
							currentPacketSize + " and data size " + (currentPacketSize - 3));
					
					numReceivedFileBytes += (currentPacketSize - 3);
				}

			}
			
			if (DEBUG_MODE) {
				System.out.println("DEBUG: Finished receiving packets.");
				System.out.println("DEBUG: Recieved file size is " + numReceivedFileBytes +
						" bytes.");
			}
			
			writer.close();
			
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
			
		}
		
		return true;
		
	}		
		
	/**
	 * The main method for running the Receiver1 class. There are two arguments -
	 * the port number to receive on and the name of the file to write.
	 * 
	 * @param args		The program arguments: the port number and the name of the 
	 * 					file that is to be sent.
	 */
	public static void main(String[] args) {
		
		int portNumber;
		String fileName = null;
		boolean successful = false;
		
		/* Both arguments are compulsory. */
		if (args.length != 2) {
			System.err.println("Usage: java Receiver1 port filename");
			System.exit(-1);
		}
		
		try {
			
			portNumber = Integer.parseInt(args[0]);
			fileName = args[1];
			
			/* Attempt to receive data and write it to the given file. */
			Receiver1 receiver = new Receiver1(fileName, portNumber);
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
