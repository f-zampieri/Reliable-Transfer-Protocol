public class SendManagerRunnable implements Runnable {

	private FXVSocket fxv;
	private Object lock;
	private int threadToInterrupt;
	private Object threadToInterruptLock;

	public SendManagerRunnable(FXVSocket fxvSocket, Object lock, Object threadToInterruptLock) {
		this.fxv = fxvSocket;
		this.lock = lock;
		this.threadToInterruptLock = threadToInterruptLock;
	}

	public int getThreadToInterrupt() {
		synchronized(threadToInterruptLock) {
			return threadToInterrupt;
		}
	}

	public void setThreadToInterrupt(int value) {
		synchronized(threadToInterruptLock) {
			this.threadToInterrupt = value;
		}
	}

	public void run() {
		//The send threads are used to send each of the packets. These threads
        //essentially form the sliding window. The thread index variable 
        //is used to indicate the thread that can currently be used. 
        Thread[] sendThreads = new Thread[PacketUtilities.WINDOW_SIZE];
        int threadIndex = 0;

        //If the base - head is less than the window size, we can send
        //more packets through the window.
        int windowBase = 0;
        int windowHead = 0;
        synchronized(lock) {
        	windowHead = fxv.getSendWindowHead();
        	windowBase = fxv.getSendWindowBase();
        }
        while(Math.abs(windowHead - windowBase) < PacketUtilities.WINDOW_SIZE) {
        	if (Thread.interrupted()) {
        		sendThreads[getThreadToInterrupt()].interrupt();
         	}
        	synchronized(lock) {
	        	PacketUtilities.SendState state = fxv.getSendBufferState()[windowHead];
	        	if(state != PacketUtilities.SendState.DONE) {
	        		if(state == PacketUtilities.SendState.READY_TO_SEND) {
	        			fxv.getSendBufferState()[windowHead] = PacketUtilities.SendState.SENDING;
	            		sendThreads[threadIndex] = new Thread(
	            			new SendRunnable(windowHead++, fxv, lock));
	            		sendThreads[threadIndex++].start();
	            		windowHead = windowHead % fxv.sendBuffer.length;
	            		threadIndex = threadIndex % PacketUtilities.WINDOW_SIZE;
	            		fxv.setSendWindowHead(windowHead);
	            		windowBase = fxv.getSendWindowBase();
	        		} else {
	        			System.out.println("This should never print." + 
	        							   "We're sending in an illegal state.");
	        		}
	       		}
	       	}
	    }
	}
}


		


