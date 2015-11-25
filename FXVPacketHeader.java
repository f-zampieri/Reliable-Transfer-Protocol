public class FXVPacketHeader {

	// TODO: may need to handle signed/unsigned values; ie values above 2^7 - 1
	public short srcPort;
	public short dstPort;
	public int   seqNumber;
	public int   ackNumber;
	public int   checksum;
	public int   payloadLength;

	private byte flags;
	private int  windowSize; // need getters and setters to avoid using first 8 bits

	public int getSrcPortAsInt() {
		return (int) (srcPort & 0xFFFF);
	}

	public int getDstPortAsInt() {
		return (int) (dstPort & 0xFFFF);
	}

	public int getWindowSize() {
		return this.windowSize;
	}

	public void setWindowSize(int size) {
		int mask = 0xFF000000 & size;
		if (mask == 0) {
			this.windowSize = size;
		} else {
			throw new IllegalArgumentException("Invalid window size (" + size +  ").");
		}
	}

	public byte getFlags() {
		return this.flags;
	}

	public void setFlags(byte flags) {
		int mask = 0xe0 & flags;
		if (mask == 0) {
			this.flags = flags;
		} else {
			throw new IllegalArgumentException("Invalid flags.");
		}		
	}

	// SYN ACK FIN AUTH RST
	public boolean getSyn() {
		return (getFlags() & 16) == 16;
	}

	public void setSyn(boolean flag) {
		this.flags = (byte) (flag ? getFlags() | 16 : getFlags() & ~16);
	}

	public boolean getAck() {
		return (getFlags() & 8) == 8;
	}

	public void setAck(boolean flag) {
		this.flags = (byte) (flag ? getFlags() | 8 : getFlags() & ~8);		
	}

	public boolean getFin() {
		return (getFlags() & 4) == 4;
	}

	public void setFin(boolean flag) {
		this.flags = (byte) (flag ? getFlags() | 4 : getFlags() & ~4);		
	}

	public boolean getAuth() {
		return (getFlags() & 2) == 2;
	}

	public void setAuth(boolean flag) {
		this.flags = (byte) (flag ? getFlags() | 2 : getFlags() & ~2);		
	}

	public boolean getRst() {
		return (getFlags() & 1) == 1;
	}

	public void setRst(boolean flag) {
		this.flags = (byte) (flag ? getFlags() | 1 : getFlags() & ~1);		
	}

	public String toString() {
		return "srcPort: " + getSrcPortAsInt() + " " 
			 + "dstPort: " + getDstPortAsInt() + " "
			 + "seqNumber: " + seqNumber + " "
			 + "ackNumber: " + ackNumber + " "
			 + "flags: " + Integer.toBinaryString(flags) + " "
			 + "windowSize: " + windowSize + " "
			 + "checksum: " + checksum + " "
			 + "payloadLength: " + payloadLength;
	}

	public static void main(String[] args) {
		// FXVPacketHeader ph = new FXVPacketHeader();
		// System.out.println(ph);
		// ph.setSyn(true);
		// System.out.println(ph);
		// ph.setSyn(false);
		// ph.setAck(true);
		// ph.setRst(true);
		// System.out.println(ph);
		// ph.setWindowSize(666);
		// System.out.println(ph);
		// // ph.setWindowSize(Integer.MAX_VALUE);
		// ph.setFlags((byte) 0x0F);
		// System.out.println(ph);
		// ph.setFlags((byte) 0xFF);
		// // it appears to work :)
	}

}
