public class FxVSocket {

	public DatagramSocket socket;
	public DatagramPacket[] buffer;
	public final WINDOW_SIZE = 1;
	//Constructor to create an FxVSocket
	
	public FxVSocket() {
		socket = new DatagramSocket();
	}

	public void bind(SocketAddress addr) {
		socket.bind(addr);
	}

	public SocketAddress getSocketAddress() {
		return socket.getLocalAddress();
	}

	public FxVSocket connect(SocketAddress address) {
		socket.connect(addr);
		//Need to perform the 4 way handshake here
		//and allocate resources i.e. the buffer if
		//the handshake is successful.
	}


}