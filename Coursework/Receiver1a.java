import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Receiver1a {

	public static void main(String[] args) throws Exception {

		// Values provided by user
		int port = Integer.valueOf(args[0]);
		String fileName = args[1];

		// Set to false when whole file has been received
		Boolean stillReceiving = true;
		// Will save the received file
		File outputFile = new File(fileName);
		FileOutputStream output = new FileOutputStream(outputFile);

		DatagramSocket socket = new DatagramSocket(port);
		DatagramPacket packet;
		// Buffer for saving the data in the packet
		byte buffer[] = new byte[1027];
		// What the data from the packet will be moved to
		byte packetArray[];

		System.out.println("Waiting for sender");
		do {
			// Receive the packet from the socket
			packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			packetArray = packet.getData();
			// Write the data in the packet to the output file
			output.write(packetArray, 3, packet.getLength() - 3);
			// If received the end-of-file tag, then exit loop
			if (packetArray[2] == 1) {
				stillReceiving = false;
			}
		} while (stillReceiving);

		System.out.println("File saved as " + fileName);
		// Close the socket and the file output stream
		output.close();
		socket.close();
	}
}
