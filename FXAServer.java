import java.io.File;
import java.io.FileInputStream;

public class FXAServer {

    public static final int COMMAND_LENGTH = 75;
    public static final int FILE_SIZE_LENGTH = 10;

    public static byte[] removePadding(byte[] data) {
        //Removing the padded zeroes and getting the actual data
        int index = 0;
        for (int i = 0; i < COMMAND_LENGTH; i++) {
            if (data[i] == 0) {
                index = i;
            }
        }
        byte[] actualData = new byte[index];
        for (int i = 0; i < index; i++) {
            actualData[i] = data[i];
        }
        return actualData;
    }

    public static byte[] addPadding(byte[] data, int size) {
        byte[] paddedData = new byte[size];
        for(int i = 0; i < paddedData.length; i++) {
            if(i < data.length) {
                paddedData[i] = data[i];
            } else {
                System.out.println("DATA TOO BIG");
            }
        }
        return paddedData;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Wrong number of arguments.");
        }

        int portNumber = Integer.parseInt(args[0]);
        String[] ipAddressString = args[1].split("\\.");
        byte[] ipAddress = {Byte.parseByte(ipAddressString[0]),
                            Byte.parseByte(ipAddressString[1]),
                            Byte.parseByte(ipAddressString[2]),
                            Byte.parseByte(ipAddressString[3])};
        int netEmuPortNumber = Integer.parseInt(args[2]);

        FXVSocket socket = new FXVSocket(portNumber);

        // <COMMAND>:<FILE NAME>
        while (true) {
            if (socket.accept()) {
                System.out.println("Connection opened.");
                // byte[] receiveData = socket.read(COMMAND_LENGTH);
                byte[] receiveData = socket.read();
                System.out.println(receiveData == null);

                String command = new String(removePadding(receiveData));
                String[] commandComponents = command.split(":");
                if (commandComponents[0].equals("get")) {
                    File theFile = new File(commandComponents[1]);
                    FileInputStream fileStream = new FileInputStream(theFile);
                    //Assumed that the file size is not greater than 32
                    //We send a fileSize of 32 bytes as a standard size so that
                    //the receiver can read the file size before actually reading
                    //the file itself.
                    byte[] fileSizeData = new Long(theFile.length()).toString().getBytes();
                    socket.write(addPadding(fileSizeData, FILE_SIZE_LENGTH));
                    byte[] fileData = new byte[PacketUtilities.PACKET_SIZE];
                    while (fileStream.available() > 0) {
                        fileStream.read(fileData);
                        // create packet, send that shit
                        socket.write(fileData);
                    }
                } else if (command.equals("post")) {
                    boolean eof = false;
                    while (!eof) {
                        byte[] receiveFile = socket.read(16);

                    }
                }
            }
        }
    }

}