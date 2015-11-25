public class SendManagerRunnable implements Runnable {

	private FXVSocket fxv;
	private Object lock;
	Thread[] sendThreads;
    int threadIndex;

	public SendManagerRunnable(FXVSocket fxvSocket, Object lock) {
		this.fxv = fxvSocket;
		this.lock = lock;
		sendThreads = new Thread[PacketUtilities.WINDOW_SIZE];
		threadIndex = 0;
	}


	public void run() {
		//The send manager thread performs the following tasks:
        //1. Using slave threads sends from the buffer constantly. 
        //2. Updates the send header base and maintains window size.
        //3. Sleeps if nothing to be sent for a while.

        while(true) {
        	int windowBase = 0;
        	int windowHead = 0;
    		synchronized(lock) {
    			windowBase = fxv.getSendWindowBase();
    			windowHead = fxv.getSendWindowHead();
    		}
		        //If the base - head is less than the window size, we can send
	        //more packets through the window. Base will move due to receive
	        //method.
    		while(Math.abs(windowBase - windowHead) 
    			< PacketUtilities.WINDOW_SIZE) {

    			PacketUtilities.SendState state = PacketUtilities.SendState.NOT_INITIALIZED;
				synchronized(lock) {
					state = fxv.getSendBufferState()[windowHead];
    			}
	    		//Checks to see if more data is ready to be sent.
        		if(state == PacketUtilities.SendState.READY_TO_SEND) {
        			synchronized(lock) {
	        			fxv.setSendBufferState(windowHead,
        		                    	   PacketUtilities.SendState.SENDING);
            			sendThreads[threadIndex] = new Thread(
			           		new SendRunnable(windowHead++, fxv, lock));
	        			windowHead = windowHead % fxv.sendBuffer.length;
        				threadIndex = threadIndex % PacketUtilities.WINDOW_SIZE;
        				fxv.setSendWindowHead(windowHead);
							//If no more data to send and all the slave sending threads
        				//are done sending then we can sleep this thread.
        				//The send manager is notified when there is no data from
        				//the read method.
        			}
            	} else if ( ( state == PacketUtilities.SendState.DONE)
            	        &&  ( (windowBase - windowHead) == 0) ) {
        	        try {
        	        	//Chosen arbitrary value 10000
            			Thread.sleep(10000);
        			} catch(InterruptedException e) {
            			continue;
        			}
            	}
    		}
	    }
	}
}


		


