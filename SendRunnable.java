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
		byte[] receiveData = new byte[PacketUtilities.HEADER_SIZE];

        //TODO: Try and avoid the null declaration.
        byte[] fxvSendPacketBytes = null;

    	DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
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
                PacketUtilities.SendState stateOfPacket = fxvSocket.getSendBufferState()[this.index];
                if(stateOfPacket == PacketUtilities.SendState.SENDING) {
                    DatagramSocket socket = fxvSocket.socket;
                    try {
                        socket.send(sendPacket);
                    } catch (IOException e) {}
                } else if(stateOfPacket == PacketUtilities.SendState.ACKED) {
                    isAcked = true;
                } else {
                    //The code should never reach this block. 
                    System.out.println("Packet somehow in send thread in weird state");
                }
            }
            try {
                Thread.sleep(500);
            }
            catch(InterruptedException e) {
                isAcked = true;
            }
        }
	}
}