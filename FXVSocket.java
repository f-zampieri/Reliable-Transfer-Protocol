import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

public class FXVSocket {

	public DatagramSocket socket;
	public DatagramPacket[] buffer;
	public final int WINDOW_SIZE = 1;

	private int windowBase;
	private int windowHead;
	private Random random;
	private MessageDigest md;

	//Constructor to create an FxVSocket
	public FXVSocket() throws SocketException, NoSuchAlgorithmException {
		this.socket = new DatagramSocket();
		this.windowBase = 0;
		this.windowHead = 0;
		this.random = new Random();
		this.md = MessageDigest.getInstance("MD5");
	}

	public FXVSocket(int portNumber) throws SocketException, NoSuchAlgorithmException {
		this.socket = new DatagramSocket(portNumber);
		this.windowBase = 0;
		this.windowHead = 0;
		this.random = new Random();
		this.md = MessageDigest.getInstance("MD5");
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
			new DatagramPacket(PacketUtilities.serialize(header),
							   PacketUtilities.HEADER_SIZE, address);

		socket.send(sendPacket);
		socket.setSoTimeout(1000);

		// 2. RECEIVES ACK + AUTH (FROM SERVER, accept())
		byte[] receiveData = new byte[PacketUtilities.HEADER_SIZE + 64];
		DatagramPacket receivePacket
			= new DatagramPacket(receiveData, receiveData.length);

        boolean gotAck = false;

        //TODO: Try and get rid of this null thing
        FXVPacket authAckPacket = null;
        FXVPacketHeader authAckPacketHeader = null;
        while (!gotAck) {
            try {
                socket.receive(receivePacket);
                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags
                authAckPacket = new FXVPacket(receiveData);
                authAckPacketHeader = authAckPacket.getHeader();
                gotAck = PacketUtilities.validateChecksum(authAckPacket)
                		&& authAckPacketHeader.ackNumber == initialSeqNumber + 1
                		&& authAckPacketHeader.getFlags() == 10;
                System.out.println(new String(authAckPacket.getData()));
            } catch (SocketTimeoutException e) {
            	// TODO: handle server dying
                socket.send(sendPacket);
                continue;
            }
        }

        md.update(authAckPacket.getData());
        byte[] digest = md.digest();

		// 3. SENDS ACK + AUTH BACK

        header = new FXVPacketHeader();
        header.setAuth(true);
        header.setAck(true);
        header.ackNumber = authAckPacketHeader.seqNumber + 1;
        header.seqNumber = initialSeqNumber + 1;
        header.payloadLength = digest.length;
        header.srcPort = (short) socket.getLocalPort();
		header.dstPort = (short) address.getPort();
		FXVPacket sendingAuthAckPacket = new FXVPacket(header);
		sendingAuthAckPacket.setChecksum(PacketUtilities.computeChecksum(authAckPacket));
		byte[] sendingAuthAckPacketBytes = sendingAuthAckPacket.toByteArray();
		sendPacket = new DatagramPacket(sendingAuthAckPacketBytes,
										sendingAuthAckPacketBytes.length,
										receivePacket.getSocketAddress());
		socket.send(sendPacket);



        

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
			if (PacketUtilities.validateChecksum(new FXVPacket(receiveHeader))) {
				if (receiveHeader.getFlags() == 16) {
					connectionSuccessful = true;

					sendHeader = new FXVPacketHeader();
					// 64-byte challenge string
					sendHeader.payloadLength = 64;
					int initialSeqNumber = random.nextInt();
					sendHeader.seqNumber = initialSeqNumber;
					sendHeader.ackNumber = receiveHeader.seqNumber + 1;
					sendHeader.srcPort = receiveHeader.dstPort;
					sendHeader.dstPort = receiveHeader.srcPort;
					sendHeader.setAuth(true);
					sendHeader.setAck(true);
					// set checksum!

					// compute message digest
					String challenge = PacketUtilities.generateRandomChallenge();
					byte[] challengeBytes = challenge.getBytes();
					md.update(challengeBytes);
			        byte[] digest = md.digest();

					fxvSendPacket = new FXVPacket(sendHeader);
					fxvSendPacket.setData(challengeBytes);

					fxvSendPacket.setChecksum(PacketUtilities.computeChecksum(fxvSendPacket));

					fxvSendPacketBytes = fxvSendPacket.toByteArray();
					sendPacket = new DatagramPacket(fxvSendPacketBytes,
													fxvSendPacketBytes.length,
													receivePacket.getSocketAddress());

					socket.send(sendPacket);
					
					boolean gotAck = false;
					receiveData = new byte[PacketUtilities.HEADER_SIZE + md.getDigestLength()];

					//TODO: Get rid of the null thing
			        FXVPacket authAckPacket = null;
			        FXVPacketHeader authAckPacketHeader = null;
			        while (!gotAck) {
			            try {
			            	//TODO: Fix this and remove the variable 40.
			            	receiveData = new byte[40];
							receivePacket = new DatagramPacket(receiveData, PacketUtilities.HEADER_SIZE);

			                socket.receive(receivePacket);
			                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags

			                authAckPacket = new FXVPacket(receiveData);
			                authAckPacketHeader = authAckPacket.getHeader();
			                System.out.println(authAckPacketHeader);
			                System.out.println(authAckPacket.getData());

			                //TODO: Stuff breaks here. We need to figure out why checksum doesnt validate correctly.
			                gotAck = PacketUtilities.validateChecksum(authAckPacket)
			                		&& authAckPacketHeader.ackNumber == initialSeqNumber + 1
			                		&& authAckPacketHeader.getFlags() == 10;
			                System.out.println(gotAck);
			                System.out.println(new String(authAckPacket.getData()));
			            } catch (SocketTimeoutException e) {
			            	// TODO: handle server dying
			                socket.send(sendPacket);
			                continue;
			            }
			        }

			        if (Arrays.equals(digest, authAckPacket.getData())) {
			        	System.out.println("we have passed the challenge");
			        }
				}
			}
		}

		// TODO: before you wreck yourself
		return null;
	}

}