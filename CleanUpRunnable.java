public class CleanUpRunnable implements Runnable {

	private FXVSocket fxvSocket;

	public CleanUpRunnable(FXVSocket fxvSocket) {
		this.fxvSocket = fxvSocket;
	}

	
	public void run() {
		while (true) {
			if (fxvSocket.getSendIsAckedBuffer()[fxvSocket.getSendWindowHead()] == true) {
				fxvSocket.incrementSendWindowHead();
			}
		}		
	}

}