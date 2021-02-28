/* Stephen McGruer 0840449 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * The Sender2 class represents a transmission of a number of bytes via
 * the UDP protocol using a send-and-wait policy. After each packet is
 * sent an acknowledgement packet (ack) must be received before the next
 * packet is sent. Packet numbers are either 0 or 1, alternating.
 * <p>
 * The outgoing packet protocol is as follows:
 * <ul>
 * <li>The first two bytes are the packet number (giving a value range of 0 to 65536).
 * <li>The next byte is the EOF byte. A value of anything but 0 signifies an EOF packet.
 * <li>The remaining (<a href="#PACKET_SIZE">PACKET_SIZE</a> - 3) bytes are filled with data.
 * </ul>
 * <p>
 * The ack packet is merely a 1 or 0 to note the packet number.
 * <p>
 * Note that the ack-receiving port is hard-coded to be the outgoing port + 1, as the
 * coursework does not specify any way of setting when calling the program.
 * 
 * @author s0840449
 *
 */
public class Sender2 {
	
	/** Defines the size of a packet, in bytes. Usually 1024, must be at least 4 or no
	 * data will be transferred. */
	public static final int PACKET_SIZE = 1024;
	
	/* Using a constant value for DEBUG_MODE allows the compiler to
	 * optimise away the if statements that rely on it and thus remove
	 * all performance hits when debugging is off. */
	private static final boolean DEBUG_MODE = false;
	
	/* The amount of time (in ms) we wait for an ack between each
	 * sending of a packet. */
	private static final int TIMEOUT_LENGTH = 90;
	
	/* The receiving host name. */
	private String host;
	
	/* The target port number on the receiving host. */
	private int portNumber;
	
	/* Sender and ack socket. */
	private DatagramSocket senderSocket;
	private DatagramSocket ackSocket;
	
	/* Used to read data from the input file. */
	private FileInputStream reader;
	
	/* Used to track the number of timeouts for coursework question. */
	private int retransmissions;
	
	/**
	 * Default constructor.
	 * 
	 * @param host			The receiving host.
	 * @param portNumber	The port number on the receiving host.
	 */
	public Sender2(String host, int portNumber) {
		this.host = host;
		this.portNumber = portNumber;
		
		this.retransmissions = 0;
	}
	
	/**
	 * Attempt to send the data contained in a file. Uses the stop-and-wait
	 * protocol, where after each packet is sent we wait for an ack to arrive
	 * back for that packet before we send the next packet.
	 * 
	 * @param dataFile		The file to get the data from.
	 * 
	 * @return	True if the sending succeeded, False if an error occurred.
	 * 			A return value of True does <b>not</b> guarantee that the 
	 * 			data made it successfully to the receiver, only that an 
	 * 			ack was received for every packet sent.
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
			ackSocket = new DatagramSocket(portNumber + 1);
			
			InetAddress ipAddress = InetAddress.getByName(host);
		
			/* Since the packet number cycles between 0 and 1, we only need
			 * a byte of space. */
			byte packetNum = 0;
			
			boolean ackRecieved = false;
			
			/* Tracks whether we are done with the transfer or not (i.e. if we've
			 * sent the final packet. */
			boolean finishedTransfer = false;
			
			/* Used to monitor throughput for the coursework question. */
			long before = System.currentTimeMillis();
			
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
				sendData[0] = 0;
				sendData[1] = packetNum;
							
				/* EOF byte */
				sendData[2] = (byte) (finishedTransfer ? 1 : 0);
								
				/* The data bytes. */
				reader.read(sendData, 3, datalen);
				
				if (DEBUG_MODE) {
					numSentFileBytes += datalen;
				}

				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
																ipAddress, portNumber);
				
				/* Reduce by one because we just increment it every time we even send a 
				 * packet, not just when we re-transmit. */
				retransmissions--;
				
				/* Keep sending until the correct ack is received. */
				while (!ackRecieved) {
					
					retransmissions++;
					
					senderSocket.send(sendPacket);
					
					if (DEBUG_MODE) {
						System.out.println("DEBUG: Sent packet " + packetNum + " with size "
								+ sendPacket.getLength() + " and data size " + (sendPacket.getLength() - 3));
					}
					
					ackRecieved = waitForAck(packetNum, ackSocket);
					
				}
				
				if (DEBUG_MODE) {
					System.out.println("DEBUG: Recieved ack for packet " + packetNum + ".");
				}
							
				ackRecieved = false;
				
				/* Cycle between packet numbers 0 and 1. */
				packetNum = (byte) ((packetNum + 1) % 2);
				
			}

			/* Used to monitor throughput for the coursework question. */
			long now = System.currentTimeMillis();
			
			/* Calculate the throughput. */
			double timeTaken = (now - before) / 1000.0;
			double kBSent = dataFileLength / 1024.0;
			System.out.println("Throughput: " + (kBSent / timeTaken) + " kB/s");
			
			if (DEBUG_MODE) {
				System.out.println("DEBUG: Finished sending packets.");
				System.out.println("DEBUG: " + dataFileLength + " bytes should have been sent.");
				System.out.println("DEBUG: " + numSentFileBytes + " bytes were sent.");
			}
			
			System.out.println("Number of re-transmissions that occured: " + retransmissions);
			
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
			ackSocket.close();
		}
		
		return true;
	}

	/**
	 * Waits for an ack packet of the given packet number on a socket,
	 * timing out after a set amount of time.
	 * 
	 * @param packetNum			The packet number we want an ack for.
	 * @param ackSocket			The socket to listen on for packets.
	 * @return					True if a correct ack was received, false
	 * 							if we either timed-out or the received ack
	 * 							had the incorrect packet number.
	 */
	private boolean waitForAck(byte packetNum, DatagramSocket ackSocket) {
		
		try {

			byte[] ackBuffer = new byte[1];
			DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

			/* Retransmission timeout. */
			ackSocket.setSoTimeout(TIMEOUT_LENGTH);	
			
			ackSocket.receive(ackPacket);
			
			byte[] ackData = ackPacket.getData();
			
			if (DEBUG_MODE) {
				System.out.println("DEBUG: Recieved ack for packet num " + ackData[0]);
			}
			
			return (ackData[0] == packetNum);
			
		} catch (SocketTimeoutException ste) {
			
			/* Timeouts are not errors! */
			if (DEBUG_MODE) { 
				System.out.println("DEBUG: Timeout when checking for acks.");
			}
			
			return false;
			
		} catch (IOException ioe) {
			
			System.err.println("Error: " + ioe.getMessage());
			return false;
			
		} 
			
		
	}

	/**
	 * The main method for running the Sender2 class. There are three arguments -
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
			System.err.println("Usage: java Sender2 host_name port filename");
			System.exit(-1);
		}
		
		try {
			
			host = args[0];
			portNumber = Integer.parseInt(args[1]);
			filePath = args[2]; 
			
			/* Attempt to send the file. */
			Sender2 sender = new Sender2(host, portNumber);
			successful = sender.send(new File(filePath));
			
		} catch (NumberFormatException nfe) {
			System.err.println("Error: Unable to convert input port number \"" + args[1] +
			   "\" to an integer.");
		} catch (IllegalArgumentException iae) {
			System.err.println("Error: " + iae.getMessage());
		}
		
		if (successful) {
			System.out.println("Success! File has been sent.");
		} else {
			System.err.println("File was not sent successfully. Please try again.");
		}

	}

}
