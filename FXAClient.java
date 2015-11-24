import java.net.*;
import java.util.Scanner;

public class FXAClient {

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
        for(int i = 0; i < index; i++) {
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
        String[] ipAddressString = args[2].split("\\.");
        byte[] ipAddress = {Byte.parseByte(ipAddressString[0]),
                            Byte.parseByte(ipAddressString[1]),
                            Byte.parseByte(ipAddressString[2]),
                            Byte.parseByte(ipAddressString[3])};
        int netEmuPortNumber = Integer.parseInt(args[2]);

        FXVSocket socket = new FXVSocket(portNumber);

        // <COMMAND> : <FILE NAME>
        Scanner sc = new Scanner(System.in);
       	String command = sc.next();
        while(!command.equals("disconnect")) {
        	String[] commandComponents = command.split(" ");

        	//TODO: Have to deal with cases where parameters are weird and
        	//dont give enough data.
        	//TODO: Also make sure that connect happens before get.

        	if(commandComponents[0].equals("get")) {
        		byte[] commandData = command.getBytes();
                socket.send(addPadding(commandData, COMMAND_LENGTH));
                byte[] fileSizeBytes = socket.receive();
        		int fileSize = Integer.parseInt(new String(removePadding(fileSizeBytes)));
        		//TODO: You have file size. Read it bitch!

        	} else if (commandComponents[0].equals("connect")) {
        		socket.connect(new InetSocketAddress(InetAddress.getByAddress(ipAddress), portNumber));
        	}
        }
	}

}