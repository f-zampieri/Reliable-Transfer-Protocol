import java.net.*;

public class ClientDriver {

	public static void main(String[] args) throws Exception {
		FXVSocket socket = new FXVSocket();	
		byte[] ipAddress = {127, 0, 0, 1};
		socket.connect(new InetSocketAddress(InetAddress.getByAddress(ipAddress), 9591));
		byte[] data = new byte[200];
		for(int i = 0; i < data.length; i++) {
			data[i] = (byte) (i % 128);
		}
		socket.write(data);
	}
}