import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class Client {
	DatagramSocket socket;
	static final int RETRY_LIMIT = 4;	/* 
	 * UTILITY METHODS PROVIDED FOR YOU 
	 * Do NOT edit the following functions:
	 *      exitErr
	 *      checksum
	 *      checkFile
	 *      isCorrupted  
	 *      
	 */

	/* exit unimplemented method */
	public void exitErr(String msg) {
		System.out.println("Error: " + msg);
		System.exit(0);
	}	

	/* calculate the segment checksum by adding the payload */
	public int checksum(String content, Boolean corrupted)
	{
		if (!corrupted)  
		{
			int i; 
			int sum = 0;
			for (i = 0; i < content.length(); i++)
				sum += (int)content.charAt(i);
			return sum;
		}
		return 0;
	}


	/* check if the input file does exist */
	File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("SENDER: File does not exists"); 
			System.out.println("SENDER: Exit .."); 
			System.exit(0);
		}
		return file;
	}


	/* 
	 * returns true with the given probability 
	 * 
	 * The result can be passed to the checksum function to "corrupt" a 
	 * checksum with the given probability to simulate network errors in 
	 * file transfer 
	 */
	public boolean isCorrupted(float prob)
	{ 
		double randomValue = Math.random();   
		return randomValue <= prob; 
	}



	/*
	 * The main method for the client.
	 * Do NOT change anything in this method.
	 *
	 * Only specify one transfer mode. That is, either nm or wt
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 5) {
			System.err.println("Usage: java Client <host name> <port number> <input file name> <output file name> <nm|wt>");
			System.err.println("host name: is server IP address (e.g. 127.0.0.1) ");
			System.err.println("port number: is a positive number in the range 1025 to 65535");
			System.err.println("input file name: is the file to send");
			System.err.println("output file name: is the name of the output file");
			System.err.println("nm selects normal transfer|wt selects transfer with time out");
			System.exit(1);
		}

		Client client = new Client();
		String hostName = args[0];
		int portNumber = Integer.parseInt(args[1]);
		InetAddress ip = InetAddress.getByName(hostName);
		File file = client.checkFile(args[2]);
		String outputFile =  args[3];
		System.out.println ("----------------------------------------------------");
		System.out.println ("SENDER: File "+ args[2] +" exists  " );
		System.out.println ("----------------------------------------------------");
		System.out.println ("----------------------------------------------------");
		String choice=args[4];
		float loss = 0;
		Scanner sc=new Scanner(System.in);  


		System.out.println ("SENDER: Sending meta data");
		client.sendMetaData(portNumber, ip, file, outputFile); 

		if (choice.equalsIgnoreCase("wt")) {
			System.out.println("Enter the probability of a corrupted checksum (between 0 and 1): ");
			loss = sc.nextFloat();
		} 

		System.out.println("------------------------------------------------------------------");
		System.out.println("------------------------------------------------------------------");
		switch(choice)
		{
		case "nm":
			client.sendFileNormal (portNumber, ip, file);
			break;

		case "wt": 
			client.sendFileWithTimeOut(portNumber, ip, file, loss);
			break; 
		default:
			System.out.println("Error! mode is not recognised");
		} 


		System.out.println("SENDER: File is sent\n");
		sc.close(); 
	}


	/*
	 * THE THREE METHODS THAT YOU HAVE TO IMPLEMENT FOR PART 1 and PART 2
	 * 
	 * Do not change any method signatures 
	 */

	/* TODO: send metadata (file size and file name to create) to the server 
	 * outputFile: is the name of the file that the server will create
	*/
	public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) throws IOException {

		MetaData metaData = new MetaData();
		metaData.setName(outputFile);
		metaData.setSize((int) file.length());

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
		objectStream.writeObject(metaData);

		byte[] data = outputStream.toByteArray();
		DatagramPacket sentPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
		socket = new DatagramSocket();
		socket.send(sentPacket);

		System.out.println("MetaData information is sent");

		socket.close();

	}

	/* TODO: Send the file to the server without corruption*/
	public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) throws IOException {

		DatagramSocket clientSocket = null;

		try {
			clientSocket = new DatagramSocket();
		}

		catch (SocketException e) {
			System.err.println("The socket couldn't be connected or bind to the specified port " +
					portNumber);
			System.exit(1);
		}

		Scanner reader = null;

		try {
			reader = new Scanner(file);
			}
		catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

		String input = "";

		if (reader.hasNextLine()){
			input = reader.nextLine();
		} else {
			System.err.println("Input File is empty");
			System.exit(0);
		}

		while (reader.hasNextLine()){
			input = input + "\n" + reader.nextLine();
		}

		byte[] inputbytes = input.getBytes();

		List<byte[]> ListOfByteSegments = new ArrayList<byte[]>();

		int charCount = 0;

		byte[] tempSegmentedByte = new byte[4];

		for (int i = 0; i < inputbytes.length; i++) {

			if (charCount == 4){
				ListOfByteSegments.add(tempSegmentedByte);
				charCount = 0;
				tempSegmentedByte = new byte[4];
			}

			tempSegmentedByte[charCount] = inputbytes[i]; charCount++;
		}

		if (charCount != 0){
			ListOfByteSegments.add(tempSegmentedByte);
		}

		Segment[] segmentsArray = new Segment[ListOfByteSegments.size()];

		for (int i = 0; i < segmentsArray.length; i++) {
			segmentsArray[i] = new Segment();
			segmentsArray[i].setSize(4);
			segmentsArray[i].setSq(i % 2);
			segmentsArray[i].setType(SegmentType.Data);
			segmentsArray[i].setPayLoad((new String(ListOfByteSegments.get(i), StandardCharsets.UTF_8)).replaceAll("\0", ""));
			segmentsArray[i].setChecksum(checksum(segmentsArray[i].getPayLoad(), false));
		}

		byte[] ackBuffer = new byte[1024];
		DatagramPacket receivedPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
		Segment ack;

		for (int i = 0; i < segmentsArray.length; i++) {

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);

			ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);

			objectStream.writeObject(segmentsArray[i]);

			byte[] data = outputStream.toByteArray();

			DatagramPacket sentPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);

			clientSocket.send(sentPacket);
			clientSocket.receive(receivedPacket);

			byte[] incomingData = receivedPacket.getData();
			ByteArrayInputStream in = new ByteArrayInputStream(incomingData);
			ObjectInputStream out = new ObjectInputStream(in);

			try{

				ack = (Segment) out.readObject();

				if (ack.getType() == SegmentType.Ack && ack.getSq() == segmentsArray[i].getSq())

					System.out.println("Acknowledgment is Recieved" + ack.getSq());

				else {

					System.out.println("Acknowledgement is not recieved");

					System.exit(1);
				}

			} catch (ClassNotFoundException e) {

				e.printStackTrace();

			}

		}

		clientSocket.close();
		System.out.println("File is sent as normal");


		//String response = new String(receivedPacket.getData()).trim();
		//System.out.println("Response from server: " + response);

		//exitErr("sendFileNormal is not implemented");
	} 

	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is 
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) throws IOException {

		DatagramSocket clientSocket = null;

		try {
			clientSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.err.println("The socket couldn't be connected or bind to the specified port " +
					portNumber);
			System.exit(1);
		}

		Scanner reader = null;

		try {
			reader = new Scanner(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

		String input = "";

		if (reader.hasNextLine()) {
			input = reader.nextLine();
		} else {
			System.err.println("Input File is empty");
			System.exit(0);
		}

		while (reader.hasNextLine()) {
			input = input + "\n" + reader.nextLine();
		}

		byte[] inputbytes = input.getBytes();

		List<byte[]> ListOfByteSegments = new ArrayList<byte[]>();

		int charCount = 0;

		byte[] tempSegmentedByte = new byte[4];

		for (int i = 0; i < inputbytes.length; i++) {

			if (charCount == 4) {
				ListOfByteSegments.add(tempSegmentedByte);
				charCount = 0;
				tempSegmentedByte = new byte[4];
			}

			tempSegmentedByte[charCount] = inputbytes[i];
			charCount++;
		}

		if (charCount != 0) {
			ListOfByteSegments.add(tempSegmentedByte);
		}

		Segment[] segmentsArray = new Segment[ListOfByteSegments.size()];

		for (int i = 0; i < segmentsArray.length; i++) {
			segmentsArray[i] = new Segment();
			segmentsArray[i].setSize(4);
			segmentsArray[i].setSq(i % 2);
			segmentsArray[i].setType(SegmentType.Data);
			segmentsArray[i].setPayLoad((new String(ListOfByteSegments.get(i), StandardCharsets.UTF_8)).replaceAll("\0", ""));
			segmentsArray[i].setChecksum(checksum(segmentsArray[i].getPayLoad(), isCorrupted(loss)));
		}

		System.out.println(isCorrupted(loss));

		if (isCorrupted(loss) == true) {
			System.out.println("Segments corruption has occurred");
		} else {
			System.out.println("Segments corruption has not occurred");
		}

		byte[] ackBuffer = new byte[1024];
		DatagramPacket receivedPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
		Segment ack;

		TimeoutException timeoutException = new TimeoutException();

		int timeOut = 5000;

		int tryCount = 0;

		int acks = 0;

		boolean file_recieved = false;

		while (tryCount != 4 && file_recieved == false) {

			tryCount = tryCount + 1;

			System.out.println(tryCount);

			for (int i = 0; i < segmentsArray.length; i++) {

				try {
					clientSocket.setSoTimeout(timeOut);

					ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);

					ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);

					objectStream.writeObject(segmentsArray[i]);

					byte[] data = outputStream.toByteArray();

					DatagramPacket sentPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);

					clientSocket.send(sentPacket);
					clientSocket.receive(receivedPacket);

					byte[] incomingData = receivedPacket.getData();
					ByteArrayInputStream in = new ByteArrayInputStream(incomingData);
					ObjectInputStream out = new ObjectInputStream(in);

					try {


						ack = (Segment) out.readObject();

						if (ack.getType() == SegmentType.Ack && ack.getSq() == segmentsArray[i].getSq()) {

							System.out.println("Acknowledgement is recieved" + ack.getSq());
							acks = acks + 1;

						} else {
							System.out.println("Acknowledgement is not recieved");
						}

					} catch (ClassNotFoundException e) {
						System.out.println("socket not found");
					}


					//catch (ClassNotFoundException e) {
					//System.out.println("socket failure");
					//}

				} catch (SocketTimeoutException e) {
					System.out.println("timeout reached");

				}

				if (acks == 3) {
					System.out.println("File was sent with no issues");
					file_recieved = true;
				}

			}

		}


		clientSocket.close();
		System.exit(1);

		//exitErr("sendFileWithTimeOut is not implemented");

	}

}