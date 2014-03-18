package org.maneulyori.seoip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLSocketFactory;

public class ConnectionThread implements Runnable {
	private SSLSocketFactory sslSocketFactory;
	private Socket socket;
	private String addr;
	private boolean isSSL;
	private int port;
	private String key;
	private boolean terminate = false;

	private Timer keepalive;
	private BufferedReader socketReader;
	private PrintStream socketPrintStream;
	private LinkedList<byte[]> APDUQueue = new LinkedList<byte[]>();
	private LinkedList<String> sendQueue = new LinkedList<String>();
	private LinkedList<String> responseQueue = new LinkedList<String>();
	private LinkedList<String> readerList = new LinkedList<String>();

	private final ConnectionThread self = this;

	public ConnectionThread(SSLSocketFactory sslSocketFactory, String addr,
			int port, String key) {
		this.sslSocketFactory = sslSocketFactory;
		this.addr = addr;
		this.port = port;
		this.key = key;
		this.isSSL = true;
	}

	public ConnectionThread(SSLSocketFactory sslSocketFactory, String addr,
			int port, String key, boolean isSSL) {
		this.sslSocketFactory = sslSocketFactory;
		this.addr = addr;
		this.port = port;
		this.key = key;
		this.isSSL = isSSL;
	}

	@Override
	public void run() {
		try {
			if (isSSL) {
				this.socket = sslSocketFactory.createSocket(addr, port);
			} else {
				this.socket = new Socket(addr, port);
			}

			keepalive = new Timer();

			socketReader = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			socketPrintStream = new PrintStream(socket.getOutputStream());

			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {
					while (!terminate) {
						while (sendQueue.size() <= 0) {
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

						socketPrintStream.println(sendQueue.pop());
					}

				}

			});

			thread.start();

			sendAuth(key);

			if (!getCommandSuccess()) {
				disconnect();
			}

			while (!terminate) {
				String resp = socketReader.readLine();

				if (resp == null)
					break;

				if (resp.startsWith("PING")) {
					socketPrintStream.println("PONG");
					getCommandSuccess();
					continue;
				}

				responseQueue.add(resp);
			}

			disconnect();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean readResponse() {

		while (responseQueue.size() <= 0) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		String response = responseQueue.pop();
		String[] splittedResponse = response.split(" ");

		if (splittedResponse.length >= 1) {
			if (splittedResponse[0].equals("APDU")) {
				byte[] apduResponse = new byte[splittedResponse.length - 1];

				for (int i = 1; i < splittedResponse.length; i++) {
					apduResponse[i - 1] = (byte) Integer.parseInt(
							splittedResponse[i], 16);
				}

				APDUQueue.add(apduResponse);

				return readResponse();
			} else if (splittedResponse[0].equals("OK")) {
				return true;
			} else if (splittedResponse[0].equals("ERROR")) {
				return false;
			} else if (splittedResponse[0].equals("LIST")) {
				String readerName = response.substring(splittedResponse[0]
						.length() + splittedResponse[1].length() + 1);
				readerList.add(readerName);
				return readResponse();
			} else if (splittedResponse[0].equals("ENDLIST")) {
				return true;
			}
		}
		return false;
	}

	private void disconnect() throws IOException {
		this.terminate = true;
		socket.close();
		keepalive.cancel();
	}

	private void sendAuth(String key) {
		socketPrintStream.println("AUTH " + key);
	}

	private boolean getCommandSuccess() throws IOException {
		return socketReader.readLine().equals("OK");
	}

	public boolean sendCommand(String command) throws IOException {
		sendQueue.add(command);
		return readResponse();

	}

	public byte[] sendAPDU(byte[] apdu) throws IOException {
		StringBuilder sb = new StringBuilder();

		sb.append("APDU");

		for (int i = 0; i < apdu.length; i++) {
			sb.append(" " + String.format("%02X", apdu[i] & 0xFF));
		}

		if (sendCommand(sb.toString())) {
			if (APDUQueue.size() < 1)
				return null;
			else
				return APDUQueue.pop();
		}

		return null;
	}

	public String[] getReaderList() {
		String[] ret = (String[]) readerList.toArray();

		readerList.clear();

		return ret;
	}

	public void terminate() {
		this.terminate = true;
	}

	public boolean isConnected() {
		return socket.isConnected();
	}
}
