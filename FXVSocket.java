import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

public class FXVSocket {

    public DatagramSocket socket;
    public DatagramPacket[] sendBuffer;
    public DatagramPacket[] receiveBuffer;
    public final int WINDOW_SIZE = 1;

    private int sendWindowBase;
    private int sendWindowHead;
    private int receiveWindowBase;
    private int receiveWindowHead;
    private Random random;
    private MessageDigest md;

    private InetSocketAddress srcSocketAddress;
    private InetSocketAddress dstSocketAddress;

    //Constructor to create an FxVSocket
    public FXVSocket() throws SocketException, NoSuchAlgorithmException {
        this.socket = new DatagramSocket();
        this.random = new Random();
        this.md = MessageDigest.getInstance("MD5");
    }

    public FXVSocket(int portNumber) throws SocketException, NoSuchAlgorithmException {
        this.socket = new DatagramSocket(portNumber);
        this.random = new Random();
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
        FXVPacketHeader header = new FXVPacketHeader();
        header.setSyn(true);
        int initialSeqNumber = random.nextInt();
        header.seqNumber = initialSeqNumber;
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

        //TODO: Try and get rid of this null thing
        FXVPacket authAckPacket = null;
        FXVPacketHeader authAckPacketHeader = null;
        while (!gotAck) {
            try {
                socket.receive(receivePacket);
                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags
                authAckPacket = new FXVPacket(receiveData);
                authAckPacketHeader = authAckPacket.getHeader();
                System.out.println(authAckPacketHeader);
                gotAck = PacketUtilities.validateChecksum(authAckPacket)
                        && authAckPacketHeader.ackNumber == initialSeqNumber + 1
                        && authAckPacketHeader.getFlags() == 10;
            } catch (SocketTimeoutException e) {
                // TODO: handle server dying
                socket.send(sendPacket);
                continue;
            }
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
        while (!gotAck) {
            try {
                receiveData = new byte[PacketUtilities.HEADER_SIZE];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);

                // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags

                socket.receive(receivePacket);
                synAckPacket = new FXVPacket(receiveData);
                synAckPacketHeader = synAckPacket.getHeader();
                System.out.println(synAckPacketHeader);

                gotAck = PacketUtilities.validateChecksum(synAckPacket)
                    && synAckPacketHeader.ackNumber == sendingAuthAckPacketHeader.seqNumber + sendingAuthAckPacket.getData().length + 1
                    && synAckPacketHeader.getFlags() == 24;
            } catch (SocketTimeoutException e) {
                // TODO: handle server dying
                socket.send(sendPacket);
                continue;
            }
        }
        

        // 5. SENDS ACK
        FXVPacketHeader sendingAckPacketHeader = new FXVPacketHeader();
        sendingAckPacketHeader.setAck(true);
        curAckNumber = synAckPacketHeader.seqNumber + 1;
        sendingAckPacketHeader.ackNumber = curAckNumber;
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
        System.out.println(this.srcSocketAddress);
        System.out.println(this.dstSocketAddress);
        this.sendBuffer = new DatagramPacket[WINDOW_SIZE];
        this.receiveBuffer = new DatagramPacket[WINDOW_SIZE];
        this.sendWindowBase = 0;
        this.sendWindowHead = 0;
        this.receiveWindowBase = 0;
        this.receiveWindowHead = 0;

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

                    sendHeader = new FXVPacketHeader();
                    // 64-byte challenge string
                    sendHeader.payloadLength = 64;
                    int initialSeqNumber = random.nextInt();
                    sendHeader.seqNumber = initialSeqNumber;
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
                    
                    boolean gotAck = false;
                    receiveData = new byte[PacketUtilities.HEADER_SIZE + md.getDigestLength()];
                    receivePacket.setData(receiveData);
                    receivePacket.setLength(receiveData.length);

                    //TODO: Get rid of the null thing
                    FXVPacket authAckPacket = null;
                    FXVPacketHeader authAckPacketHeader = null;
                    while (!gotAck) {
                        try {
                            //TODO: Fix this and remove the variable 40.
                            receiveData = new byte[PacketUtilities.HEADER_SIZE + md.getDigestLength()];
                            receivePacket = new DatagramPacket(receiveData, receiveData.length);

                            socket.receive(receivePacket);
                            // we are looking for 1) a valid checksum 2) ack number 3) ACK+AUTH flags

                            authAckPacket = new FXVPacket(receiveData);
                            authAckPacketHeader = authAckPacket.getHeader();
                            System.out.println(authAckPacketHeader);

                            gotAck = PacketUtilities.validateChecksum(authAckPacket)
                                    && authAckPacketHeader.ackNumber == initialSeqNumber + challengeBytes.length + 1
                                    && authAckPacketHeader.getFlags() == 10;
                        } catch (SocketTimeoutException e) {
                            // TODO: handle server dying
                            socket.send(sendPacket);
                            continue;
                        }
                    }

                    if (Arrays.equals(digest, authAckPacket.getData())) {
                        System.out.println("we have passed the challenge");
                        // now send syn+ackcket = new FXVPacket(sendHeader);
                        FXVPacketHeader synAckPacketHeader = new FXVPacketHeader();
                        synAckPacketHeader.setSyn(true);
                        synAckPacketHeader.setAck(true);
                        synAckPacketHeader.srcPort = authAckPacketHeader.dstPort;
                        synAckPacketHeader.dstPort = authAckPacketHeader.srcPort;
                        synAckPacketHeader.seqNumber = authAckPacketHeader.ackNumber;
                        synAckPacketHeader.ackNumber = authAckPacketHeader.seqNumber + authAckPacket.getData().length + 1;
                        synAckPacketHeader.setWindowSize(WINDOW_SIZE);
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
                    }                    
                }
            }
        }

        // server has sent SYN+ACK. if it doesn't get an ACK, just data, that's cool
        // if it does get an ACK back, that's okay
        // TODO: before you wreck yourself
        this.srcSocketAddress = new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
        this.dstSocketAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
        System.out.println(this.srcSocketAddress);
        System.out.println(this.dstSocketAddress);
        this.sendBuffer = new DatagramPacket[WINDOW_SIZE];
        this.receiveBuffer = new DatagramPacket[WINDOW_SIZE];
        this.sendWindowBase = 0;
        this.sendWindowHead = 0;
        this.receiveWindowBase = 0;
        this.receiveWindowHead = 0;

        return true;
    }

}