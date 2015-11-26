import java.util.Arrays; 

public class ServerDriver {

	public static void main(String[] args) throws Exception {
		FXVSocket socket = new FXVSocket(9591);
		socket.accept();
		byte[] read = socket.read();
		byte[] read2 = socket.read();
		System.out.println("Application Layer: " + Arrays.toString(read));
		System.out.println("Application Layer: " + Arrays.toString(read2));
		byte[] read3 = socket.read();
		byte[] read4 = socket.read();
		System.out.println("Application Layer: " + Arrays.toString(read3));
		System.out.println("Application Layer: " + Arrays.toString(read4));

	}

}