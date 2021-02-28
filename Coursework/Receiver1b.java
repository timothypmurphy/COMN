import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver1b {

	public static void main(String[] args) throws Exception {

		// Values provided by user
		int port = Integer.valueOf(args[0]);
		String fileName = args[1];

		// Set to false when whole file has been received
		Boolean stillReceiving = true;
		// Will save the received file
		File outputFile = new File(fileName);
		FileOutputStream output = new FileOutputStream(outputFile);
		// Socket and packet for receiving the file
		DatagramSocket socket = new DatagramSocket(port);
		DatagramPacket packet;
		// Socket and packet for sending the ack
		DatagramSocket ackSocket = new DatagramSocket();
		DatagramPacket ack;
		// IP address of the source of the packet, used to send the ack
		InetAddress ip;
		// Number of previously received packet, used to compare with most recent
		// packet. Initially set to 2
		int receivedPacketNo = 2;
		// Number of current received packet
		int packetNo;
		// Buffer for saving the data in the packet
		byte buffer[] = new byte[1027];
		// Buffer for sending the packet number back to the source
		byte ackData[] = new byte[1];
		// What the data from the packet will be moved to
		byte packetArray[];

		System.out.println("Waiting for sender");
		do {
			// Receive the packet from the socket
			packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			packetArray = packet.getData();
			// Set the current packet number
			packetNo = packetArray[1];
			// Save the source ip
			ip = packet.getAddress();

			// If the packet number does not match the previous packet number, then a packet
			// was not lost and this packet is not a duplicate
			if (packetNo != receivedPacketNo) {
				// Write the data in the packet to the output file
				output.write(packetArray, 3, packet.getLength() - 3);
			}

			// If we received the end-of-file tag, then exit loop
			if (packetArray[2] == 1) {
				stillReceiving = false;
			}

			// Write the current packet number to the ack buffer
			ackData[0] = (byte) packetNo;
			// Send the ack packet to the source
			ack = new DatagramPacket(ackData, 1, ip, port + 1);
			ackSocket.send(ack);
			// Update the previous packet number
			receivedPacketNo = packetNo;

		} while (stillReceiving);

		System.out.println("File saved as " + fileName);
		// Close the sockets and the file output stream
		output.close();
		socket.close();
		ackSocket.close();

	}

}
