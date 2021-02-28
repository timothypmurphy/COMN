import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Sender1a {

	public static void main(String[] args) throws Exception {

		// Values given by user
		String remoteHost = args[0];
		int port = Integer.valueOf(args[1]);
		String fileName = args[2];

		// The file we will be sending
		FileInputStream input = new FileInputStream(fileName);
		DatagramSocket socket = new DatagramSocket();
		InetAddress ip = InetAddress.getByName(remoteHost);
		// Set to false when all packets have been sent
		Boolean stillSending = true;
		// long is used to store the packet number, instead of byte, so it can be right
		// shifted to split into the two bytes in the packet header
		long packetNo = -1;
		// The number of bytes of data being sent in the packet. Normally 1024, but can
		// be smaller in the last packer
		int bytesToSend;

		System.out.println("Sending file");
		do {

			packetNo++;

			bytesToSend = 1024;

			// If there are less than 1024 bytes of data left in the file, then set the
			// number of bytes in the packet accordingly
			if (input.available() < 1024) {
				bytesToSend = input.available();
			}

			// The packet will contain the number of bytes in the payload + 2 bytes for the
			// packet no and 1 byte for the end-of-file tag
			byte packetArray[] = new byte[bytesToSend + 3];
			packetArray[2] = (byte) 0;

			// For the final packet, change stillSending to false and set the end-of-file
			// byte to 1
			if (input.available() <= 1024) {
				stillSending = false;
				packetArray[2] = (byte) 1;
			}

			// Store the packet number in the first two bytes of the packet
			packetArray[1] = (byte) (packetNo);
			packetArray[0] = (byte) (packetNo >>> 8);

			// Read the file from the fourth byte, as the first 3 are reserved for the
			// packet
			// number and the end-of-file tag
			input.read(packetArray, 3, bytesToSend);

			// Create and send the packet
			DatagramPacket packet = new DatagramPacket(packetArray, packetArray.length, ip, port);
			socket.send(packet);

			// Wait for 10 seconds
			Thread.sleep(10);

		} while (stillSending);

		System.out.println("File sent successfully");
		// Close the socket and the file stream
		socket.close();
		input.close();
	}
}
