package com.challengeandresponse.remotecontrol;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.List;

import com.challengeandresponse.configfilereader.ElementNotFoundException;

/**
 * This can be used as a command line app to run one command, or interactively like Telnet session.
 * 
 * <p>Command line:<br />
 * <pre>
 * java com.challengeandresponse.remotecontrol.RemoteControlClient (/path/to/config.xml) (hostlabel) (command) (arg1) (arg2) ... (argN)<br />
 * example:<br />
 * java com.challengeandresponse.remotecontrol.RemoteControlClient /Users/jim/Projects/RandD_Projects/RemoteControl/src/configs/RemoteControlClient.xml localhost streamText 1 2 3 4 5<br />
 * </pre>
 * </p>
 * 
 * <p>Interactively:<br />
 * <pre>
 * java com.challengeandresponse.remotecontrol.RemoteControlClient (/path/to/config.xml)<br />
 * example:<br />
 * java com.challengeandresponse.remotecontrol.RemoteControlClient /Users/jim/Projects/RandD_Projects/RemoteControl/src/configs/RemoteControlClient.xml<br />
 * </pre>
 * </p>
 * 
 * <p>
 * Config file entries:<br />
 * ("remote" can be repeated as many times as necessary)<br />
 * <pre>
 * &lt;config&gt;
 * &lt;com.challengeandresponse.remotecontrol.RemoteControlClient&gt;
 *  &lt;remote&gt;
 *   &lt;label&gt;localhost&lt;/label&gt;
 *   &lt;host&gt;127.0.0.1&lt;/host&gt;
 *   &lt;port&gt;5859&lt;/port&gt;
 *   &lt;id&gt;jim&lt;/id&gt;
 *   &lt;secret&gt;jim&lt;/secret&gt;
 * &lt;/remote&gt;
 * &lt;/com.challengeandresponse.remotecontrol.RemoteControlClient&gt;
 * &lt;/config&gt;
 * </pre>
 * </p>
 * 
 * 
 * @author jim
 *
 */
public class RemoteControlClient {

	private RemoteControlClientConfig config;

	// for console communications
	private BufferedReader in;
	private PrintWriter out;

	// for the remote connection
	private BufferedReader netIn;
	private PrintWriter netOut;

	private String remoteLabel;

	private static final String NEWLINE = System.getProperty("line.separator");
	private static final int NONCE_BYTE_LENGTH = 16;
	private static final long LISTENER_THREAD_LOOP_DELAY_MSEC = 5;
	private static final long COMMAND_LINE_POST_COMMAND_SLEEP_MSEC = 2000;

	private SecureRandom sr;
	private Listener listener = null;
	private Thread readerThread = null;

	public RemoteControlClient(String configFilePath)
	throws ElementNotFoundException, RemoteControlException {
		config = new RemoteControlClientConfig(configFilePath);
		sr = new SecureRandom();
	}


	public void run(String[] args) {
		in = new BufferedReader (new InputStreamReader (System.in));
		out = new PrintWriter (new OutputStreamWriter(System.out));

		consolePrintln("RemoteControlClient");
		consolePrint("Configured remotes: ");
		List <String> labels = config.getLabels();
		for (String label : labels) 
			consolePrint(label + " ");
		consolePrintln("");

		// fetch the controller to connect to
		// either from the command line, if automating
		if (args.length >= 3) {
			remoteLabel = args[1];
		}
		// or interactively
		else {
			String inLine = "";
			while (! config.hasLabel(inLine)) {
				consolePrintln("Connect to which remote? ('.' to quit)");
				inLine = consoleReadLine();
				if (inLine.equals(RemoteControlLib.CRLF_DISCONNECT_COMMAND))
					System.exit(-1);
			}
			remoteLabel = inLine;
		}
		// configure the connection, and connect
		InetSocketAddress socketAddress = new InetSocketAddress(config.getHost(remoteLabel),config.getPort(remoteLabel));
		Socket sock = new Socket();
		String sessionID = config.getID(remoteLabel);
		String sessionNonce = generateNonce();
		long sessionTime = System.currentTimeMillis();
		String sessionHash = RemoteControlLib.generateSecureHash(RemoteControlLib.makeSignableString(sessionID, sessionNonce, sessionTime),config.getSecret(remoteLabel));
		try {
			try {
				consolePrintln("Connecting to "+remoteLabel+" at "+config.getHost(remoteLabel).getHostAddress()+":"+config.getPort(remoteLabel));
				sock.connect(socketAddress);
				netIn = new BufferedReader (new InputStreamReader (sock.getInputStream()));
				netOut = new PrintWriter(new OutputStreamWriter (sock.getOutputStream()));
				listener = new Listener(out,netIn);
				Thread t = new Thread(listener);
				t.start();
			} 
			catch (IOException e) {
				consolePrintln("Could not connect to server:"+e.getMessage());
				System.exit(-1);
			}
			consolePrintln("Connected. Authenticating.");
			netPrintln(RemoteControlLib.CRLF_ID_COMMAND+" "+sessionID);
			netPrintln(RemoteControlLib.CRLF_NONCE_COMMAND+" "+sessionNonce);
			netPrintln(RemoteControlLib.CRLF_TIME_COMMAND+" "+sessionTime);
			netPrintln(RemoteControlLib.CRLF_HASH_COMMAND+" "+sessionHash);

			// if a command to run was proffered (it will be args[1], the second item, run it with arguments and then exit
			if (args.length >= 3) {
				// there are extra arguments on the line. make them into a string.
				String concatenatedArgs = "";
				if (args.length > 3)
					for (int i = 3; i < args.length; i++)
						concatenatedArgs += args[i]+" ";
				netPrintln(RemoteControlLib.CRLF_EXEC_COMMAND+" "+args[2]+" "+concatenatedArgs);
				netPrintln(RemoteControlLib.CRLF_DISCONNECT_COMMAND);
				try {
					Thread.sleep(COMMAND_LINE_POST_COMMAND_SLEEP_MSEC);
				} 
				catch (InterruptedException e) {
				}
			}
			// if a command was not presented, go to interactive mode and stay there until disconnect
			else {
				String consoleIn = "";
				while (sock.isConnected() && (! consoleIn.equals(RemoteControlLib.CRLF_DISCONNECT_COMMAND))) {
					consolePrint("> ");
					consoleIn = consoleReadLine();
					netPrintln(consoleIn);
				}
			}
			consolePrintln("Closing connection");
		}
		finally {
			if (listener != null)
				listener.shutdown();
			try {
				if (readerThread != null)
					readerThread.join();
				sock.close();
			}
			catch(IOException e) {
			} 
			catch (InterruptedException e) {
			}
		}
		consolePrintln("exit RemoteControlClient");
	}

	private String consoleReadLine() {
		try {
			return in.readLine();
		} 
		catch (IOException e) {
		}
		return null;
	}

	private void consolePrint(String s) {
		out.print(s);
		out.flush();
	}
	private void consolePrintln(String s) {
		out.println(s);
		out.flush();
	}

	private void netPrintln(String s) {
		netOut.println(s);
		netOut.flush();
	}


	private String generateNonce() {
		byte[] a = new byte[NONCE_BYTE_LENGTH];
		sr.nextBytes(a);
		return SHA1.encode(a);
	}



	private class Listener
	implements Runnable {

		private PrintWriter consoleOut;
		private BufferedReader netIn;
		private boolean running;

		public Listener(PrintWriter consoleOut, BufferedReader netIn) {
			this.consoleOut = consoleOut;
			this.netIn = netIn;
		}

		public void run() {
			running = true;
			while (running) {
				StringBuilder result = new StringBuilder();
				String s = null;
				try {
					while ( netIn.ready() && ((s = netIn.readLine()) != null))
						result.append(s+NEWLINE);
					consoleOut.print(result.toString());
					consoleOut.flush();
					Thread.sleep(LISTENER_THREAD_LOOP_DELAY_MSEC);
				} 
				catch (IOException e) {
				}
				catch (InterruptedException e) {
				}
			}
		}

		public void shutdown() {
			this.running = false;
			Thread.currentThread().interrupt();
		}

	}




	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Provide the path to the RemoteControlClient xml config file as an argument when starting RemoteControlClient. Cannot launch.");
			System.exit(-1);
		}
		try {
			RemoteControlClient rcc = new RemoteControlClient(args[0]);
			rcc.run(args);
		}
		catch (ElementNotFoundException e) {
			System.out.println("Exception:"+e.getMessage());
			System.exit(-1);
		}
		catch (RemoteControlException e) {
			System.out.println("Exception:"+e.getMessage());
			System.exit(-1);
		}


	}



}
