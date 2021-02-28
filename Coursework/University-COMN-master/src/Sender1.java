/* Stephen McGruer 0840449 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * The Sender1 class represents a naive transmission of a number of bytes via
 * the UDP protocol. It does no checking to determine if the packets it sends
 * reach the destination receiver (i.e. {@link Receiver1}) correctly.
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
public class Sender1 {
	
	/** Defines the size of a packet, in bytes. Usually 1024, must be at least 4 or no
	 * data will be transferred. */
	public static final int PACKET_SIZE = 1024;
	
	/* Using a constant value for DEBUG_MODE allows the compiler to
	 * optimise away the 'if' statements that rely on it and thus remove
	 * all performance hits when debugging is off. */
	private static final boolean DEBUG_MODE = true;

	/* The receiving host name. */
	private String host;
	
	/* The target port number on the receiving host. */
	private int portNumber;
	
	/* Used to send packets. */
	private DatagramSocket senderSocket;
	
	/* Used to read data from the input file. */
	private FileInputStream reader;

	/**
	 * Default constructor.
	 * 
	 * @param host			The receiving host.
	 * @param portNumber	The port number on the receiving host.
	 */
	public Sender1(String host, int portNumber) {
		this.host = host;
		this.portNumber = portNumber;
	}
	
	/**
	 * Attempt to send the data contained in a file.
	 * 
	 * @param dataFile		The file to get the data from.
	 * 
	 * @return	True if the sending succeeded, False if an error occurred.
	 * 			A return value of True does <b>not</b> guarantee that the 
	 * 			data made it successfully to the receiver (if there even 
	 * 			is one), only that the data was sent without error.
	 */
	public boolean send(File dataFile) throws IllegalArgumentException {
		
		/* Open a buffered reader around the file, checking it is okay while
		 * doing so. Note that I do no locking of the file, so writing to it
		 * while it is being sent could have nasty consequences. */
		if (!dataFile.canRead()) {
			throw new IllegalArgumentException("Cannot read input file \"" + 
					dataFile.getName() + "\". Please check file permissions and try again.");
		}
		try {
			reader = new FileInputStream(dataFile);
		} catch (FileNotFoundException fnfe) {
			throw new IllegalArgumentException("Input file \"" + dataFile.getName() +
					"\" does not exist.");
		}
		
		try {
			
			/* Debug-mode variables. */
			int numSentFileBytes = 0;
			int dataFileLength = reader.available();

			senderSocket = new DatagramSocket();
			
			InetAddress ipAddress = InetAddress.getByName(host);
			
			/* Although the packet number is 2 bytes and thus a short,
			 * we use an int in order to avoid overflow, giving us a range
			 * of 2^16 instead of (2^15)-1. */
			int packetNum = 0;
			
			/* Tracks whether or not we are in the last packet. */
			boolean finishedTransfer = false;
			
			while (!finishedTransfer) {
				
				int availableData = reader.available();

				/* If in the final packet, there may be less than 
				 * PACKET_SIZE - 3 bytes left to send. */
				int datalen = (availableData >= (PACKET_SIZE - 3)) 
								? PACKET_SIZE - 3 
								: availableData;
				
				/* Check if this is the final packet. */			
				finishedTransfer = availableData <= (PACKET_SIZE - 3);
				
				/* Holds the packet data. */
				byte sendData[] = new byte[datalen + 3];
				
				/* Packet number. */
				sendData[0] = (byte) (packetNum >>> 8);
				sendData[1] = (byte) (packetNum);
				
				/* EOF byte. */
				sendData[2] = (byte) (finishedTransfer ? 1 : 0);
				
				/* The data bytes. */
				reader.read(sendData, 3, datalen);
				
				if (DEBUG_MODE) {
					numSentFileBytes += datalen;
				}

				/* Send the packet! */
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
																ipAddress, portNumber);
				senderSocket.send(sendPacket);
				
				if (DEBUG_MODE) {
					System.out.println("DEBUG: Sent packet " + packetNum + " with size "
							+ sendPacket.getLength() + " and data size " + (sendPacket.getLength() - 3));
				}
				
				packetNum++;
				

				/* Even over a perfect connection packets can be lost at receiving sockets/etc.
				 * Un-comment out this line to give (almost certain) successful transfer when
				 * using a no-loss, 10ms prop delay ipfw connection. */
				//try { Thread.sleep(20); } catch (InterruptedException e) { }
				
			}
			
			if (DEBUG_MODE) {
				System.out.println("DEBUG: Finished sending packets.");
				System.out.println("DEBUG: " + dataFileLength + " bytes should have been sent.");
				System.out.println("DEBUG: " + numSentFileBytes + " bytes were sent.");
			}
			
		} catch (SocketException se) {
			
			System.err.println("Error: Unable to open a datagram socket:");
			System.err.println(se.getMessage());
			return false;
			
		} catch (UnknownHostException uhe) {
			
			System.err.println("Error: Unknown host \"" + host + "\".");
			return false;
			
		} catch (IOException e) {
			
			System.err.println("Error: IO Exception:");
			System.err.println(e.getMessage());
			return false;
			
		} finally {
			senderSocket.close();
		}
		
		return true;
	}

	/**
	 * The main method for running the Sender1 class. There are three arguments -
	 * the host name, port number and name of the file to send.
	 * 
	 * @param args	The program arguments: the host name, the port number, and the 
	 * 				name of the file that is to be sent.
	 */
	public static void main(String[] args) {

		String host;
		int portNumber;
		String filePath;
		boolean successful = false;

		/* All three arguments are compulsory. */
		if (args.length != 3) {
			System.err.println("Usage: java Sender1 host_name port filename");
			System.exit(-1);
		}
		
		
		try {
			
			host = args[0];
			portNumber = Integer.parseInt(args[1]);
			filePath = args[2]; 
			
			/* Attempt to send the file. */
			Sender1 sender = new Sender1(host, portNumber);
			successful = sender.send(new File(filePath));
			
		} catch (NumberFormatException nfe) {
			System.err.println("Error: Unable to convert input port number \"" + args[1] +
			   "\" to an integer.");
		} catch (IllegalArgumentException iae) {
			System.err.println("Error: " + iae.getMessage());
		} 
		
		if (successful) {
			System.out.println("File sent successfully.");
		} else {
			System.err.println("File was not sent successfully. Please try again.");
		}
		
	}

}
