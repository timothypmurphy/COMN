/* Stephen McGruer 0840449 */

/**
 * Represents an out of order packet in Receiver4 waiting to be
 * written to the output file.
 * 
 * @author s0840449
 */
public class BufferedPacket {
	
	/* The packet number. */
	private int packetNum;
	
	/* The data that was contained within the packet. */
	private byte[] data;
	
	/* Whether or not it is the eof packet. */
	private boolean eof;
	
	/**
	 * Default constructor.
	 * 
	 * @param packetNum		The packet number.
	 * @param data			The file data stored in the packet.
	 * @param eof			Whether or not it was an eof packet.
	 */
	public BufferedPacket(int packetNum, byte[] data, boolean eof) {
		this.packetNum = packetNum;
		this.data = data;
		this.eof = eof;
	}
	
	/**
	 * Return the packet number.
	 * 
	 * @return		The packet number.
	 */
	public int getPacketNum() {
		return packetNum;
	}
	
	/**
	 * Get the data stored in the packet.
	 * 
	 * @return	The file data stored in the packet.
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * Returns whether or not the packet is an eof packet.
	 * 
	 * @return		True if the packet is an eof packet, False otherwise.
	 */
	public boolean isEof() {
		return eof;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + packetNum;
		return result;
	}

	/**
	 * Two packets are equal if they have the same packet number. The
	 * file data is <b>not</b> taken into account! 
	 * 
	 * @param object		The other object to compare <i>this</i> to.
	 * 
	 * @return				True if the other object is equivalent to
	 * 						<i>this</i>, false otherwise.
	 */
	@Override
	public boolean equals(Object object) {
		
		if (this == object) {
			return true;
		}
		
		if (object == null) {
			return false;
		}
		
		if (getClass() != object.getClass()) {
			return false;
		}
		
		BufferedPacket otherBufferedPacket = (BufferedPacket) object;
		
		/* Two buffered packets are equivalent if they have the same packet number. */
		if (packetNum != otherBufferedPacket.packetNum) {
			return false;
		}
		
		return true;
		
	}

}
