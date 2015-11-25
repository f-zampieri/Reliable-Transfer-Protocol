import java.io.IOException;
import java.net.*;

public class ReceiveRunnable implements Runnable {
	private FXVSocket fxvSocket;
    private Object lock;

	public ReceiveRunnable(FXVSocket fxvSocket, Object lock) {
		this.fxvSocket = fxvSocket;
        this.lock = lock;
	}

	public void run() {
        while(true) {
            byte[] receiveData = new byte[PacketUtilities.HEADER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData,
                                                              receiveData.length);
            boolean isValid = false;
            int numTries = 0;
            FXVPacket ackPacket = null;
            FXVPacketHeader ackPacketHeader = null;
            try {
                synchronized(lock) {
                    DatagramSocket socket = this.fxvSocket.socket;
                    socket.receive(receivePacket);
                    //We are looking for 1) a valid checksum 2) ACK flags
                    ackPacket = new FXVPacket(receiveData);
                    ackPacketHeader = ackPacket.getHeader();
                    isValid = PacketUtilities.validateChecksum(ackPacket)
                                && ackPacketHeader.getFlags() == 8;
                    if(isValid) {
                        int sendWindowHead = fxvSocket.getSendWindowHead();
                        int sendWindowBase = fxvSocket.getSendWindowBase();
                        FXVPacket[] sendBuffer = fxvSocket.sendBuffer;
                        int ackNumber = ackPacketHeader.ackNumber;
                        for(int i = sendWindowBase; i < sendWindowHead; i++) {
                            if(ackNumber == sendBuffer[i].getHeader().ackNumber) {
                                this.fxvSocket.setSendBufferState((i-sendWindowBase), 
                                    PacketUtilities.SendState.ACKED);
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                continue;
            }
        }
	}
}