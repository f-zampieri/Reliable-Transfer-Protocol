public final class PacketUtilities {
	
	public static byte[] serialize(FXVPacketHeader ph) {
		byte[] data = new byte[20];
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

		return data;
	}

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

		return ph;
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