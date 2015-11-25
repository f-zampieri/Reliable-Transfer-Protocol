public class CleanUpRunnable implements Runnable {

	private FXVSocket fxvSocket;
	private Object lock;

	public CleanUpRunnable(FXVSocket fxvSocket, Object lock) {
		this.fxvSocket = fxvSocket;
		this.lock = lock;
	}

	public void run() {
		while (true) {
			synchronized(lock) {
				if (fxvSocket.getSendBufferState()[fxvSocket.getSendWindowBase()]
						 == PacketUtilities.SendState.ACKED) {
					fxvSocket.incrementSendWindowBase();
				}
			}
		}		
	}

}