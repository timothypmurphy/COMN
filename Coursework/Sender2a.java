import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Sender2a {

	static int ackPacket = -1;
	static int timeout;
	static boolean receiveAck = false;
	static int windowSize;

	public static void main(String[] args) throws Exception {

		// Values given by user
		String remoteHost = args[0];
		int port = Integer.valueOf(args[1]);
		String fileName = args[2];
		timeout = Integer.valueOf(args[3]);
		windowSize = Integer.valueOf(args[4]);
		List<PacketInfo> packetsInfo = new ArrayList<PacketInfo>();

		// The file we will be sending
		FileInputStream input = new FileInputStream(fileName);
		DatagramSocket socket = new DatagramSocket();

		DatagramPacket packet = null;
		InetAddress ip = InetAddress.getByName(remoteHost);
		// Set to false when all packets have been sent
		Boolean stillSending = true;

		// long is used to store the packet number, instead of byte, so it can be right
		// shifted to split into the two bytes in the packet header
		int packetNo = 0;
		// The number of bytes of data being sent in the packet. Normally 1024, but can
		// be smaller in the last packer
		int bytesToSend;

		ACKReceiveThread ackReceiveThread = new ACKReceiveThread(port);
		Thread ackThread = new Thread(ackReceiveThread);
		ackThread.start();

		System.out.println("Sending file");
		do {
			bytesToSend = 1024;

			try{
				if (packetsInfo.get(0).getPacketNo() <= ackPacket) {
					packetsInfo.remove(0);
				}
			} catch(IndexOutOfBoundsException e){

			}

			while (packetsInfo.size() < windowSize) {


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
				packetArray[1] = (byte) (packetNo & 0xFF);
				packetArray[0] = (byte) ((packetNo >> 8) & 0xFF);
				

				// Read the file from the fourth byte, as the first 3 are reserved for the
				// packet number and the end-of-file tag
				input.read(packetArray, 3, bytesToSend);

				// Create the packet
				packet = new DatagramPacket(packetArray, packetArray.length, ip, port);
				packetsInfo.add(new PacketInfo(packet, packetNo, System.currentTimeMillis()));
				System.out.println("PACKET " + packetNo + " SENT");
				packetNo++;
				socket.send(packet);
				receiveAck = true;

				if(!stillSending){
					break;
				}

			}
			PacketInfo currentPacket;
			for(int i = 0; i < packetsInfo.size(); i++){
				currentPacket = packetsInfo.get(i);
				if (currentPacket.getPacketNo() > ackPacket && currentPacket.getTimeOfTransmission() + timeout < System.currentTimeMillis()){
						System.out.println("Resending packet " + currentPacket.getPacketNo());
						socket.send(currentPacket.getPacket());
						//System.out.println("RESENDING PACKET " + currentPacket.getPacketNo());
						currentPacket.setTimeOfTransmission(System.currentTimeMillis());
					
				}
			}
			if(packetsInfo.size() == 0){
				receiveAck = false;
			}

		} while (stillSending);

		System.out.println("File sent successfully");

		// Close the two sockets and the file stream
		socket.close();
		receiveAck = false;

		input.close();
	}

	private static class ACKReceiveThread implements Runnable {

		DatagramSocket ackSocket;
		int finalPacket = 0;

		public ACKReceiveThread(int port) throws SocketException {
			this.ackSocket = new DatagramSocket(port + 1);
		}

		public void run() {
			// Wait until an ack is received
			boolean ack = false;
			while (receiveAck) {

				try {

					// Set timeout for ack packet and wait
					byte[] bufferAck = new byte[2];
					DatagramPacket packetAck = new DatagramPacket(bufferAck, bufferAck.length);
					ackSocket.setSoTimeout(5);
					ackSocket.receive(packetAck);
					int ackPacketNum;
					byte[] ackData = packetAck.getData();
					//System.out.println(ackData.length);


					
					ByteBuffer buffer = ByteBuffer.wrap(ackData);
					buffer.order(ByteOrder.LITTLE_ENDIAN);  // if you want little-endian
					ackPacketNum = buffer.getShort();

					if (ackPacketNum > ackPacket) {
						ackPacket = ackPacketNum;
						System.out.println("ACK received for " + ackPacket);
					}

				} catch (SocketTimeoutException ste) {
					// Go here if ack is not received in within the timeout time
					// System.out.println("ACK Timeout: Resending");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}



				// If the final packet has been sent 10 times, receiving no ack, then exit the
				// loop. Ack was presumably lost from receiver.
				if (finalPacket > 10) {
					ack = true;
				}

			}

			ackSocket.close();
		}

	}

}
