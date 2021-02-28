/* Stephen McGruer 0840449 */

import java.net.DatagramPacket;

/**
 * Represents a packet in the Sender3/4 window.
 * 
 * @author s0840449
 */
public class WindowPacket {
	
	/* The UDP packet. */
	private DatagramPacket packet;
	
	/* Whether or not the packet has been acked. */
	private boolean acked;
	
	/* The packet number. */
	private int packetNum;
	
	/* The time the packet was last sent. */
	private long timeLastSent;
	
	/**
	 * Default constructor.
	 * 
	 * @param packet		The UDP packet.
	 * @param acked			Whether or not the packet has been acked.
	 * @param packetNum		The packet number.
	 */
	public WindowPacket(DatagramPacket packet, boolean acked, int packetNum) {
		this.packet = packet;
		this.acked = acked;
		this.packetNum = packetNum;
		
		/* Set to make sure that the packet is ready to send straight away
		 * when created. */
		this.timeLastSent = 0;
	}
	
	/**
	 * Returns the UDP packet stored in the WindowPacket instance.
	 * 
	 * @return		The UDP packet.
	 */
	public DatagramPacket getPacket() {
		return packet;
	}

	/**
	 * Returns whether or not the packet has been acked.
	 * 
	 * @return		True if the packet has been acked, false otherwise.
	 */
	public boolean isAcked() {
		return acked;
	}

	/**
	 * Acks the packet. 
	 */
	public void ackPacket() {
		this.acked = true;
	}

	/**
	 * Returns the packet number.
	 * 
	 * @return		The packet number.
	 */
	public int getPacketNum() {
		return packetNum;
	}

	/**
	 * Gets the time that the packet was last sent, as milliseconds
	 * since the epoch.
	 * 
	 * @return		The epoch-time that the packet was last sent at.
	 */
	public long getTimeLastSent() {
		return timeLastSent;
	}
	
	/**
	 * Sets the time that the packet was last sent at.
	 * 
	 * @param timeLastSent		The time that the packet was last sent,
	 * 							in milliseconds since the epoch.
	 */
	public void setTimeLastSent(long timeLastSent) {
		this.timeLastSent = timeLastSent;
	}

}
