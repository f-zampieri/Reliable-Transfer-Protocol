import java.io.IOException;
import java.net.*;
import java.util.Random;

public class FXVSocket {

	public DatagramSocket socket;
	public DatagramPacket[] buffer;
	public final int WINDOW_SIZE = 1;

	private int windowBase;
	private int windowHead;
	private Random random;

	//Constructor to create an FxVSocket
	public FXVSocket() throws SocketException {
		this.socket = new DatagramSocket();
		this.windowBase = 0;
		this.windowHead = 0;
		this.random = new Random();
	}

	public FXVSocket(int portNumber) throws SocketException {
		this.socket = new DatagramSocket(portNumber);
		this.windowBase = 0;
		this.windowHead = 0;
		this.random = new Random();
	}

	public void bind(InetSocketAddress addr) throws SocketException {
		socket.bind(addr);
	}

	public InetAddress getSocketAddress() {
		return socket.getLocalAddress();
	}

	// TODO: handle exception 
	public FXVSocket connect(InetSocketAddress address) throws IOException {
		socket.connect(address);
		//Need to perform the 4 way handshake here
		//and allocate resources i.e. the buffer if
		//the handshake is successful.

		// 1. CLIENT SENDS SYN ()
		FXVPacketHeader header = new FXVPacketHeader();
		header.setSyn(true);
		int initialSeqNumber = random.nextInt();
		header.seqNumber = initialSeqNumber;
		header.srcPort = (short) socket.getLocalPort();
		header.dstPort = (short) address.getPort();
		header.checksum = PacketUtilities.computeChecksum(PacketUtilities.serialize(header));

		DatagramPacket sendPacket =
			new DatagramPacket(PacketUtilities.serialize(header), PacketUtilities.HEADER_SIZE, address);

		socket.send(sendPacket);

		// 2. RECEIVES ACK + AUTH (FROM SERVER, accept())
		// 3. SENDS ACK + AUTH BACK
		// 4. RECEIVES SYN + ACK (SERVER HAS ALLOCATED RESOURCES)
		// 5. SENDS ACK

		// TODO: check yourself
		return null;
	}

	/**
	 * Blocking call.
	 */
	public FXVSocket accept() throws IOException {
		byte[] receiveData;
		DatagramPacket receivePacket;
		DatagramPacket sendPacket;
		FXVPacketHeader receiveHeader;
		FXVPacketHeader sendHeader;
		FXVPacket fxvSendPacket;
		byte[] fxvSendPacketBytes;

		boolean connectionSuccessful = false;
		while (!connectionSuccessful) {
			receiveData = new byte[PacketUtilities.HEADER_SIZE];
			receivePacket = new DatagramPacket(receiveData, PacketUtilities.HEADER_SIZE);
			socket.receive(receivePacket);

			receiveHeader = PacketUtilities.deserialize(receiveData);
			if (PacketUtilities.validateChecksum(new FXVPacket(receiveHeader, 0))) {
				if (receiveHeader.getFlags() == 16) {
					connectionSuccessful = true;

					sendHeader = new FXVPacketHeader();
					sendHeader.seqNumber = random.nextInt();
					sendHeader.ackNumber = receiveHeader.seqNumber + 1;
					sendHeader.srcPort = receiveHeader.dstPort;
					sendHeader.dstPort = receiveHeader.srcPort;
					sendHeader.setAuth(true);
					sendHeader.setAck(true);
					// set checksum!

					// compute message digest
					String challenge = PacketUtilities.generateRandomChallenge();
					byte[] challengeBytes = challenge.getBytes();

					fxvSendPacket = new FXVPacket(sendHeader, challengeBytes.length);
					fxvSendPacket.setData(challengeBytes);

					fxvSendPacket.setChecksum(PacketUtilities.computeChecksum(fxvSendPacket));

					fxvSendPacketBytes = fxvSendPacket.toByteArray();
					sendPacket = new DatagramPacket(fxvSendPacketBytes,
													fxvSendPacketBytes.length,
													receivePacket.getSocketAddress());

					socket.send(sendPacket);
				}
			}
		}

		// TODO: before you wreck yourself
		return null;
	}

}