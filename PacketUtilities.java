import java.net.DatagramPacket;
import java.security.MessageDigest;
import java.util.Random;

/**
 * This class encapsulates methods for manipulating packet data.
 * Supported operations include FXV header retrieval, 
 * (de)serialization, checksum computation, and checksum validation. 
 * @author Francisco Zampieri, Vignish Prasad
 */
public final class PacketUtilities {

	public static final int HEADER_SIZE = 24;

	/**
	 * This method is responsible for computing the checksum. It is a reimplementation of
	 * the checksum algorithm used by UDP, except 32 bits instead of 16.
	 * @param packet The packet whose checksum is computed.
	 * @return The value of the checksum, stored in 32 bits.
	 */
	public static int computeChecksum(byte[] payload) {
		// store checksum as long. use most significant 32 bits to store overflow.
		// at the end, add the overflow back into the checksum.
		long checksum = 0;

		int currentInt = 0;
		for (int i = 0; i < payload.length; i += 4) {
			// we don't want to add the packet's stored checksum value to the
			// checksum we are in the middle of computing.
			if (i >= 16 && i <= 19) {
				i = 20;
			}
			// retrieving 4 bytes from the data at once
			for (int j = i; j < 4; j++) {
				currentInt = (payload[j]   << 24) 
						   + (payload[j+1] << 16)
						   + (payload[j+2] << 8)
						   + (payload[j+3]);
			}
			// add to the checksum
			checksum += currentInt;
			currentInt = 0;
		}
		// retrieve the overflow from the checksum
		long overflow = 0xFFFFFFFF00000000L & checksum;
		while (overflow != 0) {
			// remove the overflow from the checksum
			checksum = 0x00000000FFFFFFFFL & checksum;
			// add the overflow back to the checksum
			checksum += overflow;
			overflow = 0xFFFFFFFF00000000L & checksum;
		}
		return ~((int) checksum);
	}

	public static int computeChecksum(FXVPacket packet) {
		return computeChecksum(packet.toByteArray());
	}

	/**
	 * Validates the checksum. This is done by computing a checksum and comparing
	 * it to the value found in the packet header.
	 * @param packet The packet whose checksum will be calculated.
	 * @return Whether or not there was a bit error in transmission.
	 */
	public static boolean validateChecksum(FXVPacket packet) {
		return computeChecksum(packet) == packet.getChecksum();
	}
	
	/**
	 * Given a packet, returns its FXV protocol header.
	 * @param packet The UDP packet whose payload contains the FXV protol header.
	 * @return The given packet's FXV protocol header.
	 */
	public static FXVPacketHeader getHeaderFromPacket(byte[] payload) {
		byte[] header  = new byte[HEADER_SIZE];
		for (int i = 0; i < header.length; i++) {
			header[i] = payload[i];
		}
		return deserialize(header);
	}


	/**
	 * Given a UDP packet, retrieve the stored checksum value.
	 */
	public static int getChecksumFromPacket(byte[] packet) {
		return getHeaderFromPacket(packet).checksum;
	}

	/**
	 * Packs an FXV packet header into a byte array, whose structure 
	 * is detailed in the FXV protocol description.
	 */ 
	public static byte[] serialize(FXVPacketHeader ph) {
		byte[] data = new byte[HEADER_SIZE];
		// most significant byte of the short
		data[0] = (byte) (ph.srcPort >> 8);
		// least significant byte of the short
		data[1] = (byte) ph.srcPort;
		// etc.
		data[2] = (byte) (ph.dstPort >> 8);
		data[3] = (byte) ph.dstPort;

		data[4] = (byte) (ph.seqNumber >> 24);
		data[5] = (byte) (ph.seqNumber >> 16);
		data[6] = (byte) (ph.seqNumber >> 8);
		data[7] = (byte) ph.seqNumber;
		
		data[8]  = (byte) (ph.ackNumber >> 24);
		data[9]  = (byte) (ph.ackNumber >> 16);
		data[10] = (byte) (ph.ackNumber >> 8);
		data[11] = (byte) ph.ackNumber;

		data[12] = ph.getFlags();

		data[13] = (byte) (ph.getWindowSize() >> 16);
		data[14] = (byte) (ph.getWindowSize() >> 8);
		data[15] = (byte) ph.getWindowSize();

		data[16] = (byte) (ph.checksum >> 24);
		data[17] = (byte) (ph.checksum >> 16);
		data[18] = (byte) (ph.checksum >> 8);
		data[19] = (byte) ph.checksum;

		data[20] = (byte) (ph.payloadLength >> 24);
		data[21] = (byte) (ph.payloadLength >> 16);
		data[22] = (byte) (ph.payloadLength >> 8);
		data[23] = (byte) ph.payloadLength;

		return data;
	}

	/**
	 * Given the bytes of an FXV packet header, returns an
	 * FXVPacketHeader object encapsulating the header data.
	 */ 
	public static FXVPacketHeader deserialize(byte[] headerData) {
		FXVPacketHeader ph = new FXVPacketHeader();

		ph.srcPort = (short) ((headerData[0] << 8) + (0xFF & headerData[1]));
		ph.dstPort = (short) ((headerData[2] << 8) + (0xFF & headerData[3]));

		ph.seqNumber = ((0xFF & headerData[4]) << 24)
					+ ((0xFF & headerData[5]) << 16) 
					+ ((0xFF & headerData[6]) << 8) 
					+ (0xFF & headerData[7]);

		ph.ackNumber = ((0xFF & headerData[8]) << 24)
					+ ((0xFF & headerData[9]) << 16) 
					+ ((0xFF & headerData[10]) << 8) 
					+ (0xFF & headerData[11]);

		ph.setFlags(headerData[12]);

		int newWindowSize = 0;
		newWindowSize = ((0xFF & headerData[13]) << 16)
					  + ((0xFF & headerData[14]) << 8)
					  + (0xFF & headerData[15]);
		ph.setWindowSize(newWindowSize);

		ph.checksum = ((0xFF & headerData[16]) << 24)
					+ ((0xFF & headerData[17]) << 16) 
					+ ((0xFF & headerData[18]) << 8) 
					+ (0xFF & headerData[19]);

		ph.payloadLength = ((0xFF & headerData[20]) << 24)
						 + ((0xFF & headerData[21]) << 16) 
						 + ((0xFF & headerData[22]) << 8) 
						 + (0xFF & headerData[23]);

		return ph;
	}

    /**
     * @return String of 64 randomly generated characters.
     */
    public static String generateRandomChallenge() {
        Random r = new Random();
        char[] chars = new char[64];
        for (int i = 0; i < 64; i++) {
            // gets character in range A-Z
            chars[i] = (char) (r.nextInt('Z' - 'A') + 'A');
        }
        String res = new String(chars);
        return res;
    }

	public static void main(String[] args) {
		byte[] headerData = new byte[20];
		headerData[0] = (byte) 0xaf;
		headerData[1] = (byte) 0xae;
		headerData[2] = (byte) 0xaf;
		headerData[3] = (byte) 0xad;
		for (int i = 4; i < 16; i++) {
			headerData[i] = 0;
		}
		headerData[13] = (byte) 0xff;
		headerData[14] = (byte) 0xff;
		headerData[15] = (byte) 0xff;

		headerData[16] = (byte) 0xff;
		headerData[17] = (byte) 0xff;
		headerData[18] = (byte) 0xff;
		headerData[19] = (byte) 0xfe;
		FXVPacketHeader ph = deserialize(headerData);
		System.out.println(ph);

		byte[] newHeaderData = serialize(ph);
		for (byte b : newHeaderData) {
			System.out.println(b);
		}

	}

}