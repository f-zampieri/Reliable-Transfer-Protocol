import java.net.*;
import java.io.*;

public class ReceiveManagerRunnable implements Runnable {

    private FXVSocket fxv;
    private Object lock;

    public ReceiveManagerRunnable(FXVSocket fxvSocket, Object lock) {
        this.fxv = fxvSocket;
        this.lock = lock;
    }

    public void run() {
        while (true) {
            byte[] receiveData = new byte[PacketUtilities.HEADER_SIZE 
                                        + PacketUtilities.PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData,
                                                              receiveData.length);
            try {
                fxv.socket.receive(receivePacket);
                FXVPacket fxvReceivePacket = new FXVPacket(receiveData);
                FXVPacketHeader fxvReceivePacketHeader = fxvReceivePacket.getHeader();

                //Check various cases:
                //case 1: The packet has a reset flag set. We destroy everything.
                //case 2: The packet has a fin packet. Need to figure out what to do here.
                //case 3: The packet is normal data in which case we put it in receive buffer.
                //and ack the received packet. 

                if (PacketUtilities.validateChecksum(fxvReceivePacket)) {
                    if(fxvReceivePacketHeader.getRst()) {
                        //TERMINATE EVERYTHING SOMEHOW
                    } else if (fxvReceivePacketHeader.getFin()){
                        //TODO: Need to do this.
                    } else if (fxvReceivePacketHeader.getAck()) {
                        int ackNumber = fxvReceivePacketHeader.ackNumber;
                        //Iterating through all packets in window to check which
                        //packets sequence number corresponds to the ack number.
                        synchronized(lock) {
                            int wb = fxv.getSendWindowBase();
                            int wh = fxv.getSendWindowHead();
                            while(wb != wh) {
                                int packetExpectedAck = fxv.sendBuffer[wb].getHeader().seqNumber 
                                                      + fxv.sendBuffer[wb].getHeader().payloadLength + 1;
                                System.out.println("ACK EQUALITY" + packetExpectedAck + " " + ackNumber);
                                if (ackNumber == packetExpectedAck) {
                                    System.out.println("AckedPacket " + wb);
                                    fxv.setSendBufferState(wb, PacketUtilities.SendState.ACKED);
                                }
                                wb++;
                                wb = wb % (2 * PacketUtilities.WINDOW_SIZE);
                            }
                        }
                        //Moves the base if the first element in window is acked
                        while (fxv.getSendBufferState()[fxv.getSendWindowBase()]
                                == PacketUtilities.SendState.ACKED) {
                            fxv.incrementSendWindowBase();
                        }
                    } else {
                        //Sending the ack for any data packet that we receive
                        FXVPacketHeader sendingAckPacketHeader = new FXVPacketHeader();
                        sendingAckPacketHeader.setAck(true);
                        int curAckNumber = fxvReceivePacketHeader.seqNumber + fxvReceivePacketHeader.payloadLength + 1;
                        sendingAckPacketHeader.ackNumber = curAckNumber;
                        //Can access variables without lock since they're never touched other than in connect and accept
                        sendingAckPacketHeader.srcPort = (short) fxv.srcSocketAddress.getPort();
                        sendingAckPacketHeader.dstPort = (short) fxv.dstSocketAddress.getPort();
                        FXVPacket sendingAckPacket = new FXVPacket(sendingAckPacketHeader);
                        sendingAckPacket.setChecksum(PacketUtilities.computeChecksum(sendingAckPacket));
                        byte[] sendingAckPacketBytes = sendingAckPacket.toByteArray();
                        DatagramPacket sendPacket = new DatagramPacket(sendingAckPacketBytes,
                                        sendingAckPacketBytes.length,
                                        fxv.dstSocketAddress);
                        synchronized(lock) {
                            fxv.socket.send(sendPacket);
                        }
                        System.out.println("Sent the ack back." + sendingAckPacketHeader.ackNumber);
                        //This means that there is some data to be processed.
                        if (fxvReceivePacketHeader.payloadLength > 0) {
                            synchronized(lock) {
                                int receiveHead = fxv.getReceiveWindowHead();
                                //If there is space in the receive buffer you can put stuff in
                                if (Math.abs(receiveHead - fxv.getReceiveWindowBase()) < PacketUtilities.WINDOW_SIZE) {
                                    if ((fxv.getReceiveBufferState()[receiveHead] == PacketUtilities.ReceiveState.READ)
                                      ||(fxv.getReceiveBufferState()[receiveHead] == null)) {
                                        fxv.receiveBuffer[receiveHead] = fxvReceivePacket;
                                        fxv.getReceiveBufferState()[receiveHead] = PacketUtilities.ReceiveState.NOT_READY_TO_READ;
                                        fxv.incrementReceiveWindowHead();
                                        fxv.reorderReceiveBuffer();
                                    }
                                }
                                while((fxv.receiveBuffer[fxv.getReceiveWindowBase()] != null)
                                    && fxv.receiveBuffer[fxv.getReceiveWindowBase()].getHeader().seqNumber 
                                    == fxv.getExpectedReceiveSeqNumber()) {
                                    System.out.println("Found a match so incrementing receive window base");
                                    int newExpSeqNum = fxv.getExpectedReceiveSeqNumber() 
                                                     + fxv.receiveBuffer[fxv.getReceiveWindowBase()].getHeader().payloadLength; 
                                    fxv.setExpectedReceiveSeqNumber(newExpSeqNum);
                                    fxv.setReceiveBufferState(fxv.getReceiveWindowBase(), PacketUtilities.ReceiveState.READY_TO_READ);
                                    fxv.incrementReceiveWindowBase();
                                }
                            }
                        } else {
                            synchronized(lock) {
                                fxv.socket.send(sendPacket);
                            }
                        }

                    }
                }
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                //TODO: Why does this happen?
                System.out.println(e);
            }
        }
    }
}