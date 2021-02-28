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
import java.util.List;
import java.util.ArrayList;

/**
 * The Sender3 class represents a transmission of a number of bytes via
 * the UDP protocol using a go-back-N protocol. A window of size N is 
 * defined, which holds packets. Every TIMEOUT_LENGTH all of the unacked
 * packets are re-sent.
 * 
 * <p>
 * The outgoing packet protocol is as follows:
 * <ul>
 * <li>The first two bytes are the packet number (giving a value range of 0 to 65536).
 * <li>The next byte is the EOF byte. A value of anything but 0 signifies an EOF packet.
 * <li>The remaining (<a href="#PACKET_SIZE">PACKET_SIZE</a> - 3) bytes are filled with data.
 * </ul>
 * <p>
 * The ack packet is merely the packet number (and thus is 2 bytes).
 * <p>
 * Note that the ack-receiving port is hard-coded to be the outgoing port + 1, as the
 * coursework does not specify any way of setting when calling the program.
 * 
 * @author s0840449
 *
 */
public class Sender3 {
	
	/** Defines the size of a packet, in bytes. Usually 1024, must be at least 4 or no
	 * data will be transferred. */
	public static final int PACKET_SIZE = 1024;
	
	/* Using a constant value for DEBUG_MODE allows the compiler to
	 * optimise away the if statements that rely on it and thus remove
	 * all performance hits when debugging is off. */
	private static final boolean DEBUG_MODE = false;
	
	/* The timeout period to wait before re-sending unacked packets. */
	private static final long TIMEOUT_LENGTH = 30;
	
	/* The receiving host name. */
	private String host;
	
	/* The target port number on the receiving host. */
	private int portNumber;
	
	/* Sender socket. */
	private DatagramSocket senderSocket;
	
	/* The window size. */
	private int windowSize;
	
	/* Holds the window packets */
	private List<WindowPacket> windowPackets;
	
	/* Used to read data from the input file. */
	private FileInputStream reader;
	
	/* Flag to stop sender and ack threads. */
	public boolean stopThread;
	
	private int ackedPacketNum;

	/**
	 * Default constructor.
	 * 
	 * @param host			The receiving host.
	 * @param portNumber	The port number on the receiving host.
	 * @param windowSize	The window size to use.
	 */
	public Sender3(String host, int portNumber, int windowSize) {
		
		/* Local fields. */
		this.host = host;
		this.portNumber = portNumber;
		this.windowSize = windowSize;
		
		this.stopThread = false;
		
	}
	
	/**
	 * This thread monitors incoming acks. When an ack is received for a packet
	 * all previous packets are marked as having been acked, as the receiver must
	 * have received them previously.
	 * Log
	 * @author s0840449
	 *
	 */
	private class ACKThread extends Thread {
		
		private DatagramSocket ackSocket;
		
		/**
		 * Default constructor.
		 * 
		 * @param windowPackets		The window to ack packets in.
		 * @param portNumber		The port number to listen on for ack packets.
		 * @throws SocketException 
		 */
		public ACKThread(int portNumber) throws SocketException {
			this.ackSocket = new DatagramSocket(portNumber + 1);
		}
		
		/**
		 * The main method for the thread. Constantly waits for ack packets to arrives
		 * and acks all packets in the window with a packet number small than or equal
		 * to the ack packet. 
		 */
		public void run() {
						
			assert(windowPackets != null);
			
			/* Loop until stop signal received, grabbing ack packets (on a timeout
			 * to allow for stop signal checking) and notifying the window that
			 * they have been heard. */
			while (!stopThread) {
				
				try {
					
					if (DEBUG_MODE) {
						System.out.println("DEBUG: Checking for ack");
					}
					
					byte[] ackBuffer = new byte[2];
				
					DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

					/* Timeout so that we will keep checking for stopThread rather than
					 * just blocking forever. */
					ackSocket.setSoTimeout(5);
					ackSocket.receive(ackPacket);

					byte[] ackData = ackPacket.getData();

					/* Grab the packet number that we received an ack for. */
					int ackPacketNum = ((ackData[1] << 8) & 0x0000FF00) | 
					(ackData[0] & 0x000000FF);	

					if (DEBUG_MODE) {
						System.out.println("DEBUG: Received ack for packet " + ackPacketNum);
					}
					
					ackedPacketNum = Math.max(ackedPacketNum, ackPacketNum);

				} catch (SocketTimeoutException ste ) {
					/* Ignore timeouts. */
					if (DEBUG_MODE) {
						System.out.println("DEBUG: Timed out.");
					}
				} catch (SocketException e) {
					System.err.println("Error: Socket exception - " + e.getMessage());
				} catch (IOException e) {
					System.err.println("Error: IO exception - " + e.getMessage());
				} finally {
					Thread.yield();
				}
				
			}
			
			ackSocket.close();

			if (DEBUG_MODE) {
				System.out.println("DEBUG: Ack thread ended.");
			}
			
		}
		
	}
	
	/**
	 * Attempt to send the data contained in a file. Uses the go-back-N
	 * protocol, where a window of packets is kept which are re-sent 
	 * periodically until acked.
	 * 
	 * @param dataFile		The file to get the data from.
	 * 
	 * @return	True if the sending succeeded, False if an error occurred.
	 * 			A return value of True does <b>not</b> guarantee that the 
	 * 			data made it successfully to the receiver, only that an 
	 * 			ack was received for every packet sent. 
	 */
	public boolean send(File dataFile) {
		
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
				
		int packetNum = 1;
		
		boolean EOF = false;
		
		windowPackets = new ArrayList<WindowPacket>();
		
		try {
			
			/* Debug-mode variables. */
			int numSentFileBytes = 0;
			int dataFileLength = reader.available();
			
			senderSocket = new DatagramSocket();
			
			InetAddress ipAddress = InetAddress.getByName(host);
			
			//SendThread sendThread = new SendThread(windowPackets, senderSocket);
			ACKThread ackThread = new ACKThread(portNumber);
			
			//sendThread.start();
			ackThread.start();
			
			/* Used to monitor throughput for the coursework question. */
			long before = System.currentTimeMillis();
			
			while(!stopThread) {
				

				/* Remove acked packets. */
				while(windowPackets.size() > 0 && windowPackets.get(0).getPacketNum() <= ackedPacketNum) {
					windowPackets.remove(0);
				}
				
				/* Add new packets to the window */
				while (windowPackets.size() < windowSize && !EOF) {

					int availableData = reader.available();

					/* If in the final packet, there may be less than 
					 * PACKET_SIZE - 3 bytes left to send. */
					int datalen = (availableData >= (PACKET_SIZE - 3)) 
					? PACKET_SIZE - 3 
							: availableData;

					/* Check if this is the final packet. */			
					EOF = availableData <= (PACKET_SIZE - 3);

					/* Holds the packet data. */
					byte sendData[] = new byte[datalen + 3];

					/* Packet number. */
					sendData[0] = (byte) (packetNum >> 8);
					sendData[1] = (byte) (packetNum);

					/* EOF byte */
					sendData[2] = (byte) (EOF ? 1 : 0);

					/* The data bytes. */
					reader.read(sendData, 3, datalen);

					if (DEBUG_MODE) {
						numSentFileBytes += datalen;
					}

					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
							ipAddress, portNumber);

					windowPackets.add(new WindowPacket(sendPacket, false, packetNum));

					packetNum++;

					if (DEBUG_MODE) {
						System.out.println("DEBUG: Added new packet to window.");
					}

				}
				
				/* Send the timed out packets. */
				boolean sendPackets = false;
				for (WindowPacket packet : windowPackets) {

					/* We just ignore acked packets. */
					if (packet.getPacketNum() > ackedPacketNum) {

						/* Check if we should start sending packets. */
						sendPackets = sendPackets ||
						System.currentTimeMillis() > (packet.getTimeLastSent() + TIMEOUT_LENGTH);

						if (sendPackets) {

							if (DEBUG_MODE) {
								System.out.println("DEBUG: Sending packet " + packet.getPacketNum() +
									" with size " + packet.getPacket().getLength() +
									" and data size " +	(packet.getPacket().getLength() - 3));
							}					

							senderSocket.send(packet.getPacket());
							packet.setTimeLastSent(System.currentTimeMillis());

						} else {
							/* First unacked packet has not timed out, so break. */
							break;
						}

					}

				}



				/* Once the window is empty, we know that we have sent all possible
				 * packets - all packets had been acked and there were no more packets
				 * to add. */
				if (windowPackets.size() == 0) {
					stopThread = true;
				}
				
				Thread.yield();

			}

			if (DEBUG_MODE) {
				System.out.println("DEBUG: Finished sending packets.");
				System.out.println("DEBUG: " + dataFileLength + " bytes should have been sent.");
				System.out.println("DEBUG: " + numSentFileBytes + " bytes were sent.");
			}

			/* Used to monitor throughput for the coursework question. */
			long now = System.currentTimeMillis();
			
			/* Calculate the throughput. */
			double timeTaken = (now - before) / 1000.0;
			System.out.println("Time taken: " + timeTaken);
			double kBSent = dataFileLength / 1024.0;
			System.out.println("Data file size: " + dataFileLength + " ("  + dataFileLength/1024.0 + " kB)");
			System.out.println("Throughput: " + (kBSent / timeTaken) + " kB/s");
			
		} catch (SocketException se) {
			System.err.println("Error: Socket exception:");
			System.err.println(se.getMessage());
		} catch (UnknownHostException uhe) {
			System.err.println("Error: Host \"" + host + "\" was not found.");
		} catch (IOException e) {
			System.err.println("Error: IO Exception:");
			System.err.println(e.getMessage());
		} finally {
			
			senderSocket.close();
			stopThread = true;
			
		}
		
		return true;
		
	}

	/**
	 * The main method for running the Sender3 class. There are four arguments -
	 * the host name, port number, name of the file to send, and the window size
	 * to use.
	 * 
	 * @param args	The program arguments: the host name, the port number, the 
	 * 				name of the file that is to be sent, and the window size.
	 */
	public static void main(String[] args) {

		String host;
		int portNumber;
		String filePath;
		int windowSize;
		boolean sendSuccessful = false;

		/* All four arguments are compulsory. */
		if (args.length != 4) {
			System.err.println("Usage: java Sender3 host_name port filename windowsize");
			System.exit(-1);
		}
		
		try {
			
			host = args[0];
			portNumber = Integer.parseInt(args[1]);
			filePath = args[2];
			windowSize = Integer.parseInt(args[3]);
			
			Sender3 sender = new Sender3(host, portNumber, windowSize);
			sendSuccessful = sender.send(new File(filePath));
			
		} catch (IllegalArgumentException iae) {
			System.err.println("Error: " + iae.getMessage());
		} 
		
		if (sendSuccessful) {
			System.out.println("Success! File has been sent.");
		} else {
			System.err.println("File was not sent successfully. Please try again.");
		}

	}

}
