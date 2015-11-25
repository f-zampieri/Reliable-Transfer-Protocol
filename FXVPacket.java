public class FXVPacket {
	
	private FXVPacketHeader header;
	private byte[] payload;

	public FXVPacket(FXVPacketHeader header) {
		this.header = header;
		this.payload = new byte[PacketUtilities.PACKET_SIZE 
							  + PacketUtilities.HEADER_SIZE];
	}

	public FXVPacket(byte[] data) {
		byte[] headerData = new byte[PacketUtilities.PACKET_SIZE];
		for (int i = 0; i < headerData.length; i++) {
			headerData[i] = data[i];
		}
		this.header = PacketUtilities.deserialize(headerData);
		this.payload = new byte[this.header.payloadLength];
		// sanity check for amount of data
		if (!(data.length == PacketUtilities.HEADER_SIZE + this.header.payloadLength)) {
			System.out.println("Packet length does not check out"
								+ data.length + " "
								+ this.header.payloadLength);
		}
		for (int i = 0; i < this.header.payloadLength; i++) {
			this.payload[i] = data[i + headerData.length];
		}
	}

	public FXVPacketHeader getHeader() {
		return header;
	}

	public int getChecksum() {
		return this.header.checksum;
	}

	public void setChecksum(int checksum) {
		this.header.checksum = checksum;
	}

	public byte[] getData() {
		return payload;
	}

	public void setData(byte[] data) {
		for(int i = 0; i < data.length; i++) {
			this.payload[i] = data[i];
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.header.toString());
		sb.append(" ");
		for (byte b : this.payload) {
			sb.append(b);
		}
		return sb.toString();
	}

	public byte[] toByteArray() {
		byte[] headerData = PacketUtilities.serialize(this.header);
		byte[] result = new byte[headerData.length + this.payload.length];
		for (int i = 0; i < headerData.length; i++) {
			result[i] = headerData[i];
		}
		for (int i = headerData.length; i < result.length; i++) {
			result[i] = payload[i - headerData.length];
		}
		return result;
	}

}