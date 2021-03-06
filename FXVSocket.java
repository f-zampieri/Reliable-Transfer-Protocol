import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

public class FXVSocket {

    public DatagramSocket socket;
    public FXVPacket[] sendBuffer;
    public FXVPacket[] receiveBuffer;
    public InetSocketAddress srcSocketAddress;
    public InetSocketAddress dstSocketAddress;

    private int receiveWindowBase;
    private int receiveWindowHead;
    private int sendWindowBase;
    private int sendWindowHead;

    private int sendDataIndex;
    private int receiveDataIndex;

    private int expectedReceiveSeqNumber;

    private int seqNumber;
    private Random random;
    private MessageDigest md;

    private PacketUtilities.SendState[] sendBufferState;
    private PacketUtilities.ReceiveState[] receiveBufferState;

    public Thread sendingThread;
    public Thread receivingThread;

    public Object lock;


    //Constructor to create an FxVSocket
    public FXVSocket() throws SocketException, NoSuchAlgorithmException {
        this.socket = new DatagramSocket();
        this.random = new Random();
        this.seqNumber = random.nextInt();
        this.md = MessageDigest.getInstance("MD5");
    }

    public FXVSocket(int portNumber) throws SocketException, NoSuchAlgorithmException {
        this.socket = new DatagramSocket(portNumber);
        this.random = new Random();
        this.seqNumber = random.nextInt();
        this.md = MessageDigest.getInstance("MD5");
    }

    public void bind(InetSocketAddress addr) throws SocketException {
        socket.bind(addr);
    }

    public InetSocketAddress getSrcSocketAddress() {
        return this.srcSocketAddress;
    }

    public InetSocketAddress getDstSocketAddress() {
        return this.dstSocketAddress;
    }

    // TODO: handle exception 
    public boolean connect(InetSocketAddress address) throws IOException {
        socket.connect(address);
        //Need to perform the 4 way handshake here
        //and allocate resources i.e. the buffer if
        //the handshake is successful.

        // 1. CLIENT SENDS SYN ()
        if(PacketUtilities.DEBUG) {
            System.out.println("Sending Syn");
        }
        FXVPacketHeader header = new FXVPacketHeader();
        header.setSyn(true);
        header.seqNumber = this.seqNumber;
        header.srcPort = (short) socket.getLocalPort();
        header.dstPort = (short) address.getPort();
        header.checksum = PacketUtilities.computeChecksum(PacketUtilities.serialize(header));

        DatagramPacket sendPacket =
            new DatagramPacket(PacketUtilities.serialize(header),
                               PacketUtilities.HEADER_SIZE, address);

        socket.send(sendPacket);
        socket.setSoTimeout(1000);

        // 2. RECEIVES ACK + AUTH (FROM SERVER, accept())
        byte[] receiveData = new byte[PacketUtilities.HEADER_SIZE + 64];
        DatagramPacket receivePacket
            = new DatagramPacket(receiveData, receiveData.length);

        boolean gotAck = false;
        int numTries = 0;
        //TODO: Try and get rid of this null thing
        FXVPacket authAckPacket = null;
        FXVPacketHeader authAckPacketHeader = null;
        while (!gotAck) {
            try {
                socket.receive(receivePacket);
                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags
                authAckPacket = new FXVPacket(receiveData);
                authAckPacketHeader = authAckPacket.getHeader();
                gotAck = PacketUtilities.validateChecksum(authAckPacket)
                        && authAckPacketHeader.ackNumber == seqNumber + 1
                        && authAckPacketHeader.getFlags() == 10;
            } catch (SocketTimeoutException e) {
                // TODO: handle server dying
                numTries++;
                if (numTries >= PacketUtilities.TOTAL_TRIES) {
                    throw new SocketException("Connection timed out.");
                }
                socket.send(sendPacket);

                continue;
            }
        }
        if(PacketUtilities.DEBUG) {
            System.out.println("Received Auth Ack. Sending Auth Ack.");
        }

        md.update(authAckPacket.getData());
        byte[] digest = md.digest();

        // 3. SENDS ACK + AUTH BACK

        FXVPacketHeader sendingAuthAckPacketHeader = new FXVPacketHeader();
        sendingAuthAckPacketHeader.setAuth(true);
        sendingAuthAckPacketHeader.setAck(true);
        int curAckNumber = authAckPacketHeader.seqNumber + authAckPacket.getData().length + 1;
        sendingAuthAckPacketHeader.ackNumber = curAckNumber;
        sendingAuthAckPacketHeader.seqNumber = authAckPacketHeader.ackNumber;
        sendingAuthAckPacketHeader.payloadLength = md.getDigestLength();
        sendingAuthAckPacketHeader.srcPort = (short) socket.getLocalPort();
        sendingAuthAckPacketHeader.dstPort = (short) address.getPort();
        FXVPacket sendingAuthAckPacket = new FXVPacket(sendingAuthAckPacketHeader);
        sendingAuthAckPacket.setData(digest);
        sendingAuthAckPacket.setChecksum(PacketUtilities.computeChecksum(sendingAuthAckPacket));
        byte[] sendingAuthAckPacketBytes = sendingAuthAckPacket.toByteArray();
        sendPacket = new DatagramPacket(sendingAuthAckPacketBytes,
                                        sendingAuthAckPacketBytes.length,
                                        receivePacket.getSocketAddress());

        socket.send(sendPacket);

        FXVPacket synAckPacket = null;
        FXVPacketHeader synAckPacketHeader = null;

        // 4. RECEIVES SYN + ACK (SERVER HAS ALLOCATED RESOURCES)
        gotAck = false;
        numTries = 0;
        while (!gotAck) {
            try {
                receiveData = new byte[PacketUtilities.HEADER_SIZE];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);

                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags

                socket.receive(receivePacket);
                synAckPacket = new FXVPacket(receiveData);
                synAckPacketHeader = synAckPacket.getHeader();
                gotAck = PacketUtilities.validateChecksum(synAckPacket)
                    && synAckPacketHeader.ackNumber == sendingAuthAckPacketHeader.seqNumber + sendingAuthAckPacketHeader.payloadLength + 1
                    && synAckPacketHeader.getFlags() == 24;
            } catch (SocketTimeoutException e) {
                // TODO: handle server dying
                numTries++;
                if (numTries >= PacketUtilities.TOTAL_TRIES) {
                    throw new SocketException("Connection timed out.");
                }
                socket.send(sendPacket);
                continue;
            }
        }
        
        if(PacketUtilities.DEBUG) {
            System.out.println("Received the Syn Ack. Sending last ack");
        }
        

        // 5. SENDS ACK
        FXVPacketHeader sendingAckPacketHeader = new FXVPacketHeader();
        sendingAckPacketHeader.setAck(true);
        curAckNumber = synAckPacketHeader.seqNumber + 1;
        sendingAckPacketHeader.ackNumber = curAckNumber;
        this.expectedReceiveSeqNumber = curAckNumber + 1;
        sendingAckPacketHeader.seqNumber = synAckPacketHeader.ackNumber;
        sendingAckPacketHeader.srcPort = (short) socket.getLocalPort();
        sendingAckPacketHeader.dstPort = (short) address.getPort();
        FXVPacket sendingAckPacket = new FXVPacket(sendingAckPacketHeader);
        sendingAckPacket.setChecksum(PacketUtilities.computeChecksum(sendingAckPacket));
        byte[] sendingAckPacketBytes = sendingAckPacket.toByteArray();
        sendPacket = new DatagramPacket(sendingAckPacketBytes,
                                        sendingAckPacketBytes.length,
                                        receivePacket.getSocketAddress());

        socket.send(sendPacket);

        // TODO: check yourself
        this.srcSocketAddress = new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
        this.dstSocketAddress = address;
        this.sendBuffer = new FXVPacket[2 * PacketUtilities.WINDOW_SIZE];
        this.receiveBuffer = new FXVPacket[2 * PacketUtilities.WINDOW_SIZE];
        this.sendBufferState = new PacketUtilities.SendState[2 * PacketUtilities.WINDOW_SIZE];
        this.receiveBufferState = new PacketUtilities.ReceiveState[2 * PacketUtilities.WINDOW_SIZE];
        this.sendWindowBase = 0;
        this.sendWindowHead = 0;
        this.receiveWindowBase = 0;
        this.receiveWindowHead = 0;
        this.seqNumber += 2 + md.getDigestLength();
        this.sendDataIndex = 0;
        this.receiveDataIndex = 0;
        this.lock = new Object();
        this.sendingThread = new Thread(new SendManagerRunnable(this, lock));
        this.sendingThread.start();
        this.receivingThread = new Thread(new ReceiveManagerRunnable(this, lock));
        this.receivingThread.start();
        return true;
    }

    /**
     * Blocking call.
     */
    public boolean accept() throws IOException {
        byte[] receiveData;
        DatagramPacket receivePacket = null;
        DatagramPacket sendPacket;
        FXVPacketHeader receiveHeader;
        FXVPacketHeader sendHeader;
        FXVPacket fxvSendPacket;
        byte[] fxvSendPacketBytes;

        boolean connectionSuccessful = false;
        while (!connectionSuccessful) {
            receiveData = new byte[PacketUtilities.HEADER_SIZE];
            receivePacket = new DatagramPacket(receiveData, PacketUtilities.HEADER_SIZE);
            socket.receive(receivePacket);

            receiveHeader = PacketUtilities.deserialize(receiveData);
            if (PacketUtilities.validateChecksum(new FXVPacket(receiveHeader))) {
                if (receiveHeader.getFlags() == 16) {
                    connectionSuccessful = true;
                    if(PacketUtilities.DEBUG) {
                        System.out.println("Syn received.");
                    }

                    sendHeader = new FXVPacketHeader();
                    // 64-byte challenge string
                    sendHeader.payloadLength = 64;
                    sendHeader.seqNumber = this.seqNumber;
                    sendHeader.ackNumber = receiveHeader.seqNumber + 1;
                    sendHeader.srcPort = receiveHeader.dstPort;
                    sendHeader.dstPort = receiveHeader.srcPort;
                    sendHeader.setAuth(true);
                    sendHeader.setAck(true);
                    // set checksum!

                    // compute message digest
                    String challenge = PacketUtilities.generateRandomChallenge();
                    byte[] challengeBytes = challenge.getBytes();
                    md.update(challengeBytes);
                    byte[] digest = md.digest();

                    fxvSendPacket = new FXVPacket(sendHeader);
                    fxvSendPacket.setData(challengeBytes);

                    fxvSendPacket.setChecksum(PacketUtilities.computeChecksum(fxvSendPacket));

                    fxvSendPacketBytes = fxvSendPacket.toByteArray();
                    sendPacket = new DatagramPacket(fxvSendPacketBytes,
                                                    fxvSendPacketBytes.length,
                                                    receivePacket.getSocketAddress());

                    socket.send(sendPacket);

                    if(PacketUtilities.DEBUG) {
                        System.out.println("Sending Auth Ack. Receiving auth ack.");
                    }
 
                    boolean gotAck = false;
                    receiveData = new byte[PacketUtilities.HEADER_SIZE + md.getDigestLength()];
                    receivePacket.setData(receiveData);
                    receivePacket.setLength(receiveData.length);

                    //TODO: Get rid of the null thing
                    FXVPacket authAckPacket = null;
                    FXVPacketHeader authAckPacketHeader = null;
                    int numTries = 0;
                    while (!gotAck) {
                        try {
                            //TODO: Fix this and remove the variable 40.
                            receiveData = new byte[PacketUtilities.HEADER_SIZE + md.getDigestLength()];
                            receivePacket = new DatagramPacket(receiveData, receiveData.length);

                            socket.receive(receivePacket);
                            // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags

                            authAckPacket = new FXVPacket(receiveData);
                            authAckPacketHeader = authAckPacket.getHeader();

                            gotAck = PacketUtilities.validateChecksum(authAckPacket)
                                    && authAckPacketHeader.ackNumber == this.seqNumber + challengeBytes.length + 1
                                    && authAckPacketHeader.getFlags() == 10;
                        } catch (SocketTimeoutException e) {
                            numTries++;
                            if (numTries >= PacketUtilities.TOTAL_TRIES) {
                                throw new SocketException("Connection timed out.");
                            }
                            socket.send(sendPacket);
                            continue;
                        }
                    }

                    if(PacketUtilities.DEBUG) {
                        System.out.println("Received auth ack.");
                    }

                    if (Arrays.equals(digest, authAckPacket.getData())) {
                        if(PacketUtilities.DEBUG) {
                            System.out.println("Challenge successful");
                        }
                        // now send syn+ackcket = new FXVPacket(sendHeader);
                        FXVPacketHeader synAckPacketHeader = new FXVPacketHeader();
                        synAckPacketHeader.setSyn(true);
                        synAckPacketHeader.setAck(true);
                        synAckPacketHeader.srcPort = authAckPacketHeader.dstPort;
                        synAckPacketHeader.dstPort = authAckPacketHeader.srcPort;
                        synAckPacketHeader.seqNumber = authAckPacketHeader.ackNumber;
                        synAckPacketHeader.ackNumber = authAckPacketHeader.seqNumber + authAckPacketHeader.payloadLength + 1;
                        //ExpectedReceiveSeqNumber tells the receive and read method
                        //which packet to read next. This is set in connect.
                        this.expectedReceiveSeqNumber = synAckPacketHeader.ackNumber;
                        synAckPacketHeader.setWindowSize(PacketUtilities.WINDOW_SIZE);
                        FXVPacket synAckPacket = new FXVPacket(synAckPacketHeader);
                        synAckPacket.setChecksum(PacketUtilities.computeChecksum(synAckPacket));

                        receiveData = new byte[PacketUtilities.HEADER_SIZE];
                        receivePacket.setData(receiveData);
                        receivePacket.setLength(receiveData.length);
                        byte[] synAckPacketBytes = synAckPacket.toByteArray();
                        sendPacket = new DatagramPacket(synAckPacketBytes,
                                                        synAckPacketBytes.length,
                                                        receivePacket.getSocketAddress());
                        socket.send(sendPacket);
                        if(PacketUtilities.DEBUG) {
                            System.out.println("Sending syn ack packet. Receiving last ack.");
                        }
                        while (!gotAck) {
                            try {
                                receiveData = new byte[PacketUtilities.HEADER_SIZE];
                                receivePacket = new DatagramPacket(receiveData, receiveData.length);

                                socket.receive(receivePacket);
                                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags

                                authAckPacket = new FXVPacket(receiveData);
                                authAckPacketHeader = authAckPacket.getHeader();
                                this.expectedReceiveSeqNumber = synAckPacketHeader.ackNumber + 2;
                                gotAck = PacketUtilities.validateChecksum(authAckPacket)
                                    && authAckPacketHeader.ackNumber == synAckPacketHeader.ackNumber + 1
                                    && authAckPacketHeader.getFlags() == 8;
                            } catch (SocketTimeoutException e) {
                                numTries++;
                                if (numTries >= PacketUtilities.TOTAL_TRIES) {
                                    throw new SocketException("Connection timed out.");
                                }
                                socket.send(sendPacket);
                                continue;
                            }
                        }
                        if(PacketUtilities.DEBUG) {
                            System.out.println("Received last ack");
                        }
                    }                    
                }
            }
        }

        // server has sent SYN+ACK. if it doesn't get an ACK, just data, that's cool
        // if it does get an ACK back, that's okay
        // TODO: before you wreck yourself
        this.srcSocketAddress = new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
        this.dstSocketAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
        this.sendBuffer = new FXVPacket[2 * PacketUtilities.WINDOW_SIZE];
        this.receiveBuffer = new FXVPacket[2 * PacketUtilities.WINDOW_SIZE];
        this.sendBufferState = new PacketUtilities.SendState[2 * PacketUtilities.WINDOW_SIZE];
        this.receiveBufferState = new PacketUtilities.ReceiveState[2 * PacketUtilities.WINDOW_SIZE];
        this.sendWindowBase = 0;
        this.sendWindowHead = 0;
        this.receiveWindowBase = 0;
        this.receiveWindowHead = 0;
        this.seqNumber += 65;
        this.sendDataIndex = 0; 
        this.receiveDataIndex = 0;
        this.lock = new Object();
        this.sendingThread = new Thread(new SendManagerRunnable(this, lock));
        this.sendingThread.start();
        this.receivingThread = new Thread(new ReceiveManagerRunnable(this, lock));
        this.receivingThread.start();
        return true;
    }

    // TODO: host may not read, receive, or close before accepting/listening
    // Can potentially be a blocking call if the data to be sent is greater
    // than the buffer size.
    public void write(byte[] data) throws InterruptedException {
        //Converting data into packets. 
        FXVPacket[] fxvPackets = packetize(data);
        int numPacketsWritten = 0;

        sendingThread.interrupt();

        //Load the buffer with the more data to be sent
        while(numPacketsWritten < fxvPackets.length) {
            synchronized(lock) {
                if (sendBufferState[sendDataIndex] == null
                 || sendBufferState[sendDataIndex] == PacketUtilities.SendState.ACKED) {
                    System.out.println(fxvPackets[numPacketsWritten]);
                    sendBufferState[sendDataIndex] = PacketUtilities.SendState.READY_TO_SEND;
                    sendBuffer[sendDataIndex++] = fxvPackets[numPacketsWritten++];
                    sendDataIndex = sendDataIndex % sendBuffer.length;
                } 
            }
        }
    }


    //It can be a blocking call if not enough data in the buffer
    public byte[] read(int numBytes) {
        // byte[] data = new byte[numBytes];
        // int numBytesRead = 0;

        // while(numBytesRead < numBytes) {
        //     synchronized(lock) {
        //         if (receiveBufferState[receiveDataIndex] == PacketUtilities.ReceiveState.RECEIVED) {
        //             receiveBufferState[receiveDataIndex] = PacketUtilities.ReceiveState.READ;
        //             data[numBytesRead++] = receiveBuffer[receiveDataIndex++];
        //             receiveDataIndex = receiveDataIndex % receiveBuffer.length;
        //         }
        //     }
        // }
        return null;
    }

    //Sends back exactly one packet
    public byte[] read() {
        int numPackets = 0;
        while(numPackets == 0) {
            synchronized(lock) {
                if ((receiveBufferState[receiveDataIndex] != null)
                    && (receiveBufferState[receiveDataIndex] == PacketUtilities.ReceiveState.READY_TO_READ)) {
                    numPackets++;
                }   
            }
        }
        byte[] data = new byte[0];
        synchronized(lock) {
            data = concat(data, receiveBuffer[receiveDataIndex].getData());
            receiveBufferState[receiveDataIndex] = PacketUtilities.ReceiveState.READ;
            receiveDataIndex++;
            receiveDataIndex = receiveDataIndex % receiveBuffer.length;
        }
        return data;
    }

    private byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c= new byte[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    public byte[] receive() {
        return receive(0);
    }

    public byte[] receive(int length) {
        if (length == 0) {
            // DESIGNATED
        } else {

        }
        return null;
    }


    public void close() throws IOException {
        while (sendWindowBase - sendWindowHead > 0);

        FXVPacketHeader fxvFinPacketHeader = new FXVPacketHeader();
        fxvFinPacketHeader.setFin(true);
        fxvFinPacketHeader.seqNumber = this.seqNumber;
        fxvFinPacketHeader.srcPort = (short) this.srcSocketAddress.getPort();
        fxvFinPacketHeader.dstPort = (short) this.dstSocketAddress.getPort();
        FXVPacket fxvFinPacket = new FXVPacket(fxvFinPacketHeader);
        fxvFinPacket.setChecksum(PacketUtilities.computeChecksum(fxvFinPacket));
        byte[] fxvFinPacketBytes = fxvFinPacket.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(fxvFinPacketBytes,
                                                    fxvFinPacketBytes.length,
                                                    this.dstSocketAddress);

        this.socket.send(sendPacket);

        boolean gotAck = false;
        int numTries = 0;
        while (!gotAck) {
            try {
                byte[] receiveData = new byte[PacketUtilities.PACKET_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                // we are looking for 1) a valid checksum 2) ack number 3) ACK flags
                socket.receive(receivePacket);
                FXVPacket finAckPacket = new FXVPacket(receiveData);
                FXVPacketHeader finAckPacketHeader = finAckPacket.getHeader();

                gotAck = PacketUtilities.validateChecksum(finAckPacket)
                    && finAckPacketHeader.ackNumber == this.seqNumber + 1
                    && finAckPacketHeader.getFlags() == 8;
            } catch (SocketTimeoutException e) {
                numTries++;
                if (numTries >= PacketUtilities.TOTAL_TRIES) {
                    throw new SocketException("Connection timed out.");
                }
                socket.send(sendPacket);
                continue;
            }
        }

        this.socket.close();
    }

    private FXVPacket[] packetize(byte[] data) {
        int isDivisibleResult = ((data.length % PacketUtilities.PACKET_SIZE == 0) ? 0 : 1);
        FXVPacket[] packets = new FXVPacket[data.length / PacketUtilities.PACKET_SIZE + isDivisibleResult];
        for (int i = 0; i < packets.length - isDivisibleResult; i++) {
            FXVPacketHeader header = new FXVPacketHeader();
            header.srcPort = (short) this.srcSocketAddress.getPort();
            header.dstPort = (short) this.dstSocketAddress.getPort();
            header.seqNumber = this.seqNumber;
            //TODO: Have to figure out window size stuff.
            //int windowSize = PacketUtilities.PACKET_SIZE * (receiveBuffer.length - (receiveWindowHead - receiveWindowBase));
            //header.setWindowSize(windowSize);
            header.payloadLength = PacketUtilities.PACKET_SIZE;

            byte[] packetData = new byte[PacketUtilities.PACKET_SIZE];
            for (int j = 0; j < PacketUtilities.PACKET_SIZE; j++) {
                packetData[j] = data[j + (i * PacketUtilities.PACKET_SIZE)];
            }
            packets[i] = new FXVPacket(header);
            packets[i].setData(packetData);
            packets[i].setChecksum(PacketUtilities.computeChecksum(packets[i]));
            this.seqNumber += PacketUtilities.PACKET_SIZE;
        }
        if (data.length % PacketUtilities.PACKET_SIZE > 0) {
            FXVPacketHeader header = new FXVPacketHeader();
            header.srcPort = (short) this.srcSocketAddress.getPort();
            header.dstPort = (short) this.dstSocketAddress.getPort();
            header.seqNumber = this.seqNumber;
            int windowSize = PacketUtilities.PACKET_SIZE * (receiveBuffer.length - (receiveWindowHead - receiveWindowBase));
            header.setWindowSize(windowSize);
            int newPayloadLength = data.length % PacketUtilities.PACKET_SIZE;
            header.payloadLength = newPayloadLength;

            byte[] packetData = new byte[newPayloadLength];
            for (int j = 0; j < newPayloadLength; j++) {
                packetData[j] = data[j + PacketUtilities.PACKET_SIZE * (packets.length - 1)];
            }
            packets[packets.length - 1] = new FXVPacket(header);
            packets[packets.length - 1].setData(packetData);
            packets[packets.length - 1].setChecksum(
                PacketUtilities.computeChecksum(packets[packets.length - 1]));
            this.seqNumber += newPayloadLength;
        }
        return packets;
    }

    public PacketUtilities.SendState[] getSendBufferState() {
        return this.sendBufferState;
    }

    public void setSendBufferState(int index, PacketUtilities.SendState value) {
        this.sendBufferState[index] = value;
    }

    public int getSendWindowBase() {
        return this.sendWindowBase;
    }

    public void setSendWindowBase(int value) {
        this.sendWindowBase = value;
    }

    public int getSendWindowHead() {
        return this.sendWindowHead;
    }

    public void setSendWindowHead(int value) {
        this.sendWindowHead = value;
    }

    public void incrementSendWindowHead() {
        this.sendWindowHead++;
        this.sendWindowHead = this.sendWindowHead % sendBuffer.length;
    }

    public void incrementSendWindowBase() {
        this.sendWindowBase++;
        this.sendWindowBase = this.sendWindowBase % sendBuffer.length;
    }

    public PacketUtilities.ReceiveState[] getReceiveBufferState() {
        return this.receiveBufferState;
    }

    public void setReceiveBufferState(int index, PacketUtilities.ReceiveState value) {
        this.receiveBufferState[index] = value;
    }

    public int getReceiveWindowBase() {
        return this.receiveWindowBase;
    }

    public void setReceiveWindowBase(int value) {
        this.receiveWindowBase = value;
    }

    public int getReceiveWindowHead() {
        return this.receiveWindowHead;
    }

    public void setReceiveWindowHead(int value) {
        this.receiveWindowHead = value;
    }

    public void incrementReceiveWindowHead() {
        this.receiveWindowHead++;
        this.receiveWindowHead = this.receiveWindowHead % receiveBuffer.length;
    }

    public void incrementReceiveWindowBase() {
        this.receiveWindowBase++;
        this.receiveWindowBase = this.receiveWindowBase % receiveBuffer.length;
    }

    public int getExpectedReceiveSeqNumber() {
        return this.expectedReceiveSeqNumber;
    }

    public void setExpectedReceiveSeqNumber(int value) {
        this.expectedReceiveSeqNumber = value;
    }

    public void reorderReceiveBuffer() {
        int size = Math.abs(receiveWindowHead - receiveWindowBase);
        FXVPacket[] tempWindow = new FXVPacket[size];
        for(int i = 0; i < size; i++) {
            tempWindow[i] = receiveBuffer[i + receiveWindowBase];
        }
        Arrays.sort(tempWindow);
        for(int i = 0; i < size; i++) {
            receiveBuffer[i + receiveWindowBase] = tempWindow[i];
        }
    }



    public static void main(String[] args) throws Exception {
        // byte[] allDatData = {0x0F, 0x0C, 0x08, 0x12};
        // FXVSocket socket = new FXVSocket();
        // FXVPacket[] packets = socket.packetize(allDatData);
        // for (FXVPacket packet : packets) {
        //     System.out.println(packet.toString());
        // }
    }

}