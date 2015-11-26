import java.io.*;
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
        String[] ipAddressString = args[1].split("\\.");
        System.out.println(args[1]);
        byte[] ipAddress = {(byte) Short.parseShort(ipAddressString[0]),
                            (byte) Short.parseShort(ipAddressString[1]),
                            (byte) Short.parseShort(ipAddressString[2]),
                            (byte) Short.parseShort(ipAddressString[3])};
        int netEmuPortNumber = Integer.parseInt(args[2]);

        FXVSocket socket = new FXVSocket(portNumber);

        // <COMMAND> [<FILE NAME>]
        Scanner sc = new Scanner(System.in);
        boolean keepGoing = true;
        while (keepGoing) {
            System.out.println("Choose command: connect - get:<FILENAME> - window <SIZE> - disconnect - quit");
           	String command = sc.next();
        	String[] commandComponents = command.split(":");
            System.out.println("your command was: " + command);
        	//TODO: Have to deal with cases where parameters are weird and
        	//dont give enough data.
        	//TODO: Also make sure that connect happens before get.
            if (commandComponents.length > 0) {
            	if(commandComponents[0].equals("get")) {
                    String fileName = commandComponents[1];
                    File theFile = new File(fileName);
                    FileOutputStream outputStream = new FileOutputStream(theFile);
                    if (!theFile.exists()) {
                        theFile.createNewFile();
                    }

            		byte[] commandData = command.getBytes();
                    socket.write(addPadding(commandData, COMMAND_LENGTH));
                    byte[] fileSizeBytes = socket.receive();
            		long fileSize = Long.parseLong(new String(removePadding(fileSizeBytes)));
                    System.out.println("hello: " + fileSize);
                    while (fileSize > 0) {
                        byte[] readResult = socket.read();
                        System.out.println("hello: " + new String(readResult));
                        outputStream.write(readResult);
                        fileSize -= readResult.length;
                    }
                    outputStream.flush();
                    outputStream.close();
            		//TODO: You have file size. Read it bitch!
            	} else if (commandComponents[0].equals("connect")) {
            		socket.connect(new InetSocketAddress(InetAddress.getByAddress(ipAddress), netEmuPortNumber));
                } else if (commandComponents[0].equals("disconnect")) {
                    socket.close();
                }
            } else {
                // should fall into this case in the event of erroneous input
            	if (commandComponents[0].equals("quit")) {
                    keepGoing = false;
                    System.out.println("Thanks for playing");
                } else {
                    System.out.println("Bad input, try again.");
                }
            }
        }
	}

}