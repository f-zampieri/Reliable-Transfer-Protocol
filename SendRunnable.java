import java.io.IOException;
import java.net.*;

public class SendRunnable implements Runnable {
	private int index;
	private FXVSocket fxvSocket;
    private Object lock;

	public SendRunnable(int i, FXVSocket fxvSocket, Object lock) {
		this.index = i;
		this.fxvSocket = fxvSocket;
        this.lock = lock;
	}

	public void run() {
        byte[] fxvSendPacketBytes = null;
        synchronized(lock) {
            FXVPacket fxvSendPacket = fxvSocket.sendBuffer[index];
            fxvSendPacketBytes = fxvSendPacket.toByteArray();
        }

        //Its safe to access dstSocketAddress outside the lock because the value
        //is never changed after the connection is established. 
        DatagramPacket sendPacket = null;
        try {
            sendPacket = new DatagramPacket(fxvSendPacketBytes,
                                        fxvSendPacketBytes.length,
                                        fxvSocket.dstSocketAddress);
        } catch(SocketException e) {}

        boolean isAcked = false;
        while(!isAcked) {
            synchronized(lock) {
                PacketUtilities.SendState stateOfPacket 
                    = fxvSocket.getSendBufferState()[this.index];
                if(stateOfPacket == PacketUtilities.SendState.SENDING) {
                    DatagramSocket socket = fxvSocket.socket;
                    try {
                        socket.send(sendPacket);
                    } catch (IOException e) {}
                } else if (stateOfPacket == PacketUtilities.SendState.ACKED
                        || stateOfPacket == PacketUtilities.SendState.DONE) {
                    isAcked = true;
                } else {
                    //The code should never reach this block. 
                    System.out.println("Packet somehow in send thread in weird state");
                }
            }

            //The thread sleeps for the amount of time in the timeout.
            try { 
                Thread.sleep(PacketUtilities.SEND_TIMEOUT);
            } catch (InterruptedException e) {
                //If the packet has been acked the thread completes.
                //The send manager can create a new thread in its place.
                synchronized(lock) {
                    PacketUtilities.SendState stateOfPacket 
                        = fxvSocket.getSendBufferState()[this.index];
                    if(stateOfPacket == PacketUtilities.SendState.ACKED) {
                        isAcked = true;
                    }
                }
            }
        }
	}
}