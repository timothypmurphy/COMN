/* Stephen McGruer 0840449 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * The Receiver4 class represents a receiving of a set of bytes via
 * the UDP protocol, using a selective-repeat policy. After each received
 * packet, an acknowledgement packet (ack) is sent back confirming the
 * packet's arrival. Duplicate or out-of-window packets are discarded, 
 * out of order packets are buffered.
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
public class Receiver4 {

	/** Defines the size of a packet. Usually 1024, must be at least 4. */
	public static final int PACKET_SIZE = 1024;
	
	/* Using a constant value for DEBUG_MODE allows the compiler to
	 * optimise away the if statements that rely on it and thus remove
	 * all performance hits when debugging is off. */
	private static final boolean DEBUG_MODE = false;
	
	/* The file name to write to. */
	private String fileName;

	/* The port number to listen for data on. */
	private int portNumber;
	
	/* The window size. */
	private int windowSize;

	/* The incoming and outgoing sockets. */
	private DatagramSocket receiverSocket;
	private DatagramSocket ackSocket;
	
	/* A buffer to hold out of order packets. */
	private PriorityQueue<BufferedPacket> bufferedPackets;
	
	/**
	 * Default constructor. 
	 * 
	 * @param fileName		The output file name to write to.
	 * @param portNumber	The port number to wait for data on.
	 * @param windowSize 	The number of packets we consider "in scope" at
	 * 						any one time.
	 */
	public Receiver4(String fileName, int portNumber, int windowSize) {
		
		this.fileName = fileName;
		this.portNumber = portNumber;
		this.windowSize = windowSize;

		/* The priority queue should sort packets in order of packet number. */
		int initCapacity = 4;
		this.bufferedPackets = new PriorityQueue<BufferedPacket>(initCapacity, new Comparator<BufferedPacket>() {

			@Override
			public int compare(BufferedPacket packet1, BufferedPacket packet2) {
				
				if (packet1.equals(packet2)) {
					return 0;
				} else if (packet1.getPacketNum() < packet2.getPacketNum()) {
					return -1;
				} else {
					return 1;
				}
				 
			}
		});
		
	}
	
	/**
	 * Receives data from the set port and writes the received data to a file.
	 * Operates on a selective repeat protocol. When a packet is received, 
	 * an ack is sent back for it. At any one point in time, we consider a 
	 * certain number of continuous packets to be "in scope". Duplicate 
	 * received packets (in scope or before) are discarded. Packets that
	 * are out of order but still in scope are buffered. Packets that are out
	 * of scope are discarded.
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
		
		/* Used to check for duplicate/skipped packets. */
		int prevPacketNum = -1;
		
		/* Used to monitor the start of the packet window */
		int windowBase = 0;

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
				
				/* Only grab the data if the packet is in the window. */
				if (packetNum >= windowBase && packetNum <= windowBase + (windowSize-1)) {
					
					/* If the packet is in-order, write it to the output file and 
					 * then check for any other buffered packets that now need writing.
					 * If it is out-of-order then just buffer it (assuming it hasn't
					 * already been buffered. In both cases send an ack back.
					 */
					if (packetNum == (prevPacketNum + 1)) {
						
						/* EOF Check. */
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
						
						/* Check for any packets we now want to bring in from the buffer. */
						while (bufferedPackets.size() > 0) {
							
							BufferedPacket packet = bufferedPackets.peek();
						
							if (packet.getPacketNum() == (prevPacketNum + 1)) {
								
								if (DEBUG_MODE) {
									System.out.println("DEBUG: Buffered packet " + packet.getPacketNum() + " added to data.");
								}
								
								/* Add in the data from this packet. */
								writer.write(packet.getData(), 0, packet.getData().length);
								
								prevPacketNum = packet.getPacketNum();
								
								/* Check if we're done. */
								finishedTransfer = packet.isEof();
								
								bufferedPackets.remove();
								
							} else {
								/* Priority queue is in-order, so the first mismatching
								 * packet means we can stop. */
								break;
							}
							
						}
						
						/* Advance the window base to in front of last correctly written
						 * (*not* buffered) packet. */
						windowBase = prevPacketNum + 1;
						
					} else {
						
						if (DEBUG_MODE) {
							System.out.println("DEBUG: Out of order packet " + packetNum + " received and buffered.");
						}
						
						/* EOF check - we don't finish the transfer now since we're out of
						 * order, but we will finish when we write the eof packet from the 
						 * buffer. */
						boolean eof = false;
						if (receivedData[2] > 0) {
							eof = true;
						}
						
						/* Grab the data.*/
						byte[] data = new byte[currentPacketSize];
						for (int i = 3; i < currentPacketSize; i++) {
							data[i-3] = receivedData[i];
						}
						
						/* Create a new BufferedPacket for the queue, and add it if it doesnt
						 * already exist. */
						BufferedPacket packet = new BufferedPacket(packetNum, data, eof);
						if (!bufferedPackets.contains(packet)) {
							bufferedPackets.add(packet);
						}
						
					}
										
					/* Send an ack packet back to the sender. */
					InetAddress ipAddress = receivedPacket.getAddress();
					
					byte[] ackBuffer = new byte[2];
					ackBuffer[1] = (byte) (packetNum >>> 8);
					ackBuffer[0] = (byte) packetNum;
					
					DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length,
							ipAddress, portNumber + 1);
					ackSocket.send(ackPacket);
					
					if (DEBUG_MODE) {
						System.out.println("DEBUG: Sent ack for packet " + packetNum + ".");
					}
					
				} else if (DEBUG_MODE) {
					if (packetNum > (windowBase + windowSize - 1)) {
						System.out.println("DEBUG: Packet received ahead of window. Window base is " + windowBase +
								", packet was number " + packetNum);
					}
				}
				
				/* If <= end of window, send ack. */
				if (packetNum >= 0 && packetNum < windowBase) {

					/* Send an ack packet back to the sender. */
					InetAddress ipAddress = receivedPacket.getAddress();

					byte[] ackBuffer = new byte[2];
					ackBuffer[1] = (byte) (packetNum >>> 8);
					ackBuffer[0] = (byte) packetNum;

					DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length,
							ipAddress, portNumber + 1);
					ackSocket.send(ackPacket);

					if (DEBUG_MODE) {
						
						System.out.println("DEBUG: Received old packet " + packetNum + ".");
						System.out.println("DEBUG: Sent ack for packet " + packetNum + ".");
						
					}
					
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
	 * The main method for running the Receiver4 class. There are three arguments -
	 * the port number to receive on, the name of the file to write, and the window size
	 * 
	 * @param args		The program arguments: the port number, the 
	 * 					name of the file that is to be sent, and the window size.
	 */
	public static void main(String[] args) throws IOException {
		
		int portNumber;
		String fileName = null;
		int windowSize;
		boolean successful = false;
		
		/* All 3 arguments are compulsory. */
		if (args.length != 3) {
			System.err.println("Usage: java Receiver4 port filename windowsize");
			System.exit(-1);
		}
		
		try {
			
			portNumber = Integer.parseInt(args[0]);
			fileName = args[1];
			windowSize = Integer.parseInt(args[2]);
	
			/* Attempt to receive data and write it to the given file. */
			Receiver4 receiver = new Receiver4(fileName, portNumber, windowSize);
			successful = receiver.receive();

		} catch (IllegalArgumentException iae) {
			System.err.println("Error: " + iae.getMessage());
		}

		if (successful) {
			System.out.println("Success! A file has been received and written to " + fileName);
		} else {
			System.err.println("File was not received successfully. Please try again.");
		}
		
	}

}
