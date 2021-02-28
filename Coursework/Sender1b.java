import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class Sender1b {

	public static void main(String[] args) throws Exception {

		// Values given by user
		String remoteHost = args[0];
		int port = Integer.valueOf(args[1]);
		String fileName = args[2];

		// The file we will be sending
		FileInputStream input = new FileInputStream(fileName);
		DatagramSocket socket = new DatagramSocket();
		DatagramSocket ackSocket = new DatagramSocket(port + 1);
		InetAddress ip = InetAddress.getByName(remoteHost);
		// Set to false when all packets have been sent
		Boolean stillSending = true;
		// Set to true when ack has been received
		Boolean ack = false;
		int finalPacket = 0;
		// long is used to store the packet number, instead of byte, so it can be right
		// shifted to split into the two bytes in the packet header
		int packetNo = 0;
		// The number of bytes of data being sent in the packet. Normally 1024, but can
		// be smaller in the last packer
		int bytesToSend;

		System.out.println("Sending file");
		do {
			bytesToSend = 1024;

			// If there are less than 1024 bytes of data left in the file, then set the
			// number of bytes in the packet accordingly
			if (input.available() < 1024) {
				bytesToSend = input.available();
			}

			// The packet will contain the number of bytes in the payload + 2 bytes for the
			// packet no and 1 byte for the end-of-file tag. The first byte will always be 0
			// as the packet number can only be either 1 or 0
			byte packetArray[] = new byte[bytesToSend + 3];
			packetArray[2] = (byte) 0;

			// For the final packet, change stillSending to false and set the end-of-file
			// byte to 1
			if (input.available() <= 1024) {
				stillSending = false;
				packetArray[2] = (byte) 1;
			}

			// Store the packet number in the first two bytes of the packet
			packetArray[0] = 0;
			packetArray[1] = (byte) packetNo;

			// Read the file from the fourth byte, as the first 3 are reserved for the
			// packet number and the end-of-file tag
			input.read(packetArray, 3, bytesToSend);

			// Create the packet
			DatagramPacket packet = new DatagramPacket(packetArray, packetArray.length, ip, port);
			ack = false;

			// Wait until ack is received
			while (!ack) {

				// Send the packet
				socket.send(packet);
				try {

					// Set timeout for ack packet and wait
					byte[] bufferAck = new byte[1];
					DatagramPacket packetAck = new DatagramPacket(bufferAck, bufferAck.length);
					ackSocket.setSoTimeout(Integer.valueOf(args[3]));
					ackSocket.receive(packetAck);
					byte[] ackData = packetAck.getData();

					// If the packet number in the ack packet matches the most recently sent packet
					// number, then exit loop
					if (ackData[0] == packetNo) {
						ack = true;
					}

				} catch (SocketTimeoutException ste) {
					// Go here if ack is not received in within the timeout time
					// System.out.println("ACK Timeout: Resending");
				}

				// If the final packet has been sent 10 times, receiving no ack, then exit the
				// loop. Ack was presumably lost from receiver.
				if (finalPacket > 10) {
					ack = true;
				}

			}

			// Alternate the packet number
			if (packetNo == 0) {
				packetNo = 1;
			} else {
				packetNo = 0;
			}

		} while (stillSending);

		System.out.println("File sent successfully");

		// Close the two sockets and the file stream
		socket.close();
		ackSocket.close();
		input.close();
	}

}
