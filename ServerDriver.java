public class ServerDriver {

	public static void main(String[] args) throws Exception {
		FXVSocket socket = new FXVSocket(9591);
		socket.accept();
	}

}