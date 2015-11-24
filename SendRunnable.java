import java.io.IOException;
import java.net.*;

public class SendRunnable implements Runnable {
	private int index;
	private FXVSocket fxvSocket;

	public SendRunnable(int i, FXVSocket fxvSocket) {
		this.index = i;
		this.fxvSocket = fxvSocket;
	}

	public void run() {
		byte[] receiveData = new byte[PacketUtilities.HEADER_SIZE];
    	DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        boolean gotAck = false;
        //TODO: Try and get rid of this null thing
        DatagramSocket socket = fxvSocket.socket;
        FXVPacket fxvSendPacket = fxvSocket.sendBuffer[index];
        byte[] fxvSendPacketBytes = fxvSendPacket.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(fxvSendPacketBytes,
                                        fxvSendPacketBytes.length,
                                        fxvSocket.dstSocketAddress);
        try {
            socket.send(sendPacket);
        } catch (Exception e) {
        	
        }
        int numTries = 0;
        while (!gotAck) {
            try {
                socket.receive(receivePacket);
                // we are looking for 1) a valid checksum 2) ack number 3) ACK flags
                FXVPacket ackPacket = new FXVPacket(receiveData);
                FXVPacketHeader ackPacketHeader = ackPacket.getHeader();
                System.out.println(ackPacketHeader);
                gotAck = PacketUtilities.validateChecksum(ackPacket)
                        && ackPacketHeader.ackNumber == fxvSendPacket.getHeader().seqNumber + 1
                        && ackPacketHeader.getFlags() == 8;
                //TODO: Update the ack buffer
                fxvSocket.updateSendIsAckedBuffer(this.index);
            } catch (SocketTimeoutException e) {
                // TODO: handle server dying
                try {
                    numTries++;
                    if (numTries >= 4) {
                        throw new SocketException("Connection timed out.");
                    }
                    socket.send(sendPacket);
                    continue;
                } catch (Exception e1) {

                }
                continue;
            } catch (IOException e) {
            	continue;
            }
        }
	}
}