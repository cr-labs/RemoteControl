package com.challengeandresponse.remotecontrol;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.*;

import com.challengeandresponse.eventlogger.EventLoggerI;


/**
 * A server module for RemoteControl that accepts commands as a line of input, and outputs 
 * CRLF-terminated lines as responses... in the SMTP style.
 * 
 * Servers:
 * - receive method names and arguments from clients
 * - call back to RemoteControl to lookup and run methods
 * - route responses back to clients
 * 
 * Servers have to do this, because they implement the protocol for client interaction.
 * RemoteControl is just a shell for registering and calling methods, and launching
 * server instances
 * 
 * @author jim
 *
 */
public class CRLFServer 
implements Runnable {

	private Socket socket;
	private RemoteControl rc;
	private EventLoggerI eventLogger;

	private String line;
	private boolean running;

	private static final long READ_NOT_READY_DELAY_MSEC = 200;

	CRLFServer(Socket socket, RemoteControl rc, EventLoggerI el) {
		this.socket=socket;
		this.rc = rc;
		this.eventLogger = el;
		running = false;
	}


	public void run () {
		String id = null;
		String nonce = null;
		String hash = null;
		long time = 0L;

		PrintStream netOut = null;
		BufferedReader netIn = null;

		running = true;

		try {
			netIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			netOut = new PrintStream(socket.getOutputStream());
			netOut.println(RemoteControlLib.CRLF_CONNECTION_OPEN);
		} 
		catch (IOException e) {
			eventLogger.addEvent("IOException opening network connections:"+e.getMessage());
			running = false;
		}


		boolean hashWasChecked = false;
//		 while (running && ((line = netIn.readLine()) != null)) {
		while (running) {
			try {
				if (netIn.ready())
					line = netIn.readLine();
				else {
					try {
						Thread.sleep(READ_NOT_READY_DELAY_MSEC);
					} 
					catch (InterruptedException e) {
					}
					continue;
				}
				if (line == null) {
					running = false;
					continue;
				}
				eventLogger.addEvent("Server thread processing line:"+line);
				// tokenize the line into: command, args[]
				StringTokenizer st = new StringTokenizer(line);
				String command = null;
				ArrayList <String> args = new ArrayList<String> ();
				if (st.hasMoreTokens())
					command = st.nextToken().toLowerCase();
				else
					continue;
				while (st.hasMoreTokens())
					args.add(st.nextToken());

				if (RemoteControlLib.CRLF_ID_COMMAND.equals(command))
					id = args.get(0);
				else if (RemoteControlLib.CRLF_NONCE_COMMAND.equals(command)) {
					if (id == null) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'id' is required before setting nonce");
						continue;
					}
					if (! rc.checkNonce(args.get(0),id)) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" nonce:"+args.get(0)+" is not unique. Replay prohibited.");
						running = false;
						continue;
					}
					nonce = args.get(0);
				}
				else if (RemoteControlLib.CRLF_HASH_COMMAND.equals(command)) {
					hash = args.get(0);
					hashWasChecked = false;
				}
				else if (RemoteControlLib.CRLF_TIME_COMMAND.equals(command)) {
					try {
						time = Long.valueOf(args.get(0));
					}
					catch (NumberFormatException e) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'time' value was not valid");
						continue;
					}
					if (! rc.validTime(time)) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'time' value was not valid. Max clock skew limit exceeded.");
						continue;
					}
				}
				else if (RemoteControlLib.CRLF_DISCONNECT_COMMAND.equals(command)) {
					running = false;
					continue;
				}
				else if (RemoteControlLib.CRLF_LIST_COMMANDS_COMMAND.equals(command)) {
					StringBuilder sb =
						new StringBuilder("commands: " + RemoteControlLib.CRLF_DISCONNECT_COMMAND +
								" | " + RemoteControlLib.CRLF_LIST_COMMANDS_COMMAND +
								" | " + RemoteControlLib.CRLF_EXEC_COMMAND);
					sb.append(" | " + RemoteControlLib.CRLF_ID_COMMAND + 
							" | " + RemoteControlLib.CRLF_NONCE_COMMAND + " | " + RemoteControlLib.CRLF_HASH_COMMAND + 
							" | " + RemoteControlLib.CRLF_TIME_COMMAND);
					netOut.println(sb.toString());
					sb = new StringBuilder("methods: ");
					List <String> m = rc.getRegisteredMethods();
					for (String s : m) 
						sb.append(s+" ");
					netOut.println(sb.toString());
				}
				else if (RemoteControlLib.CRLF_EXEC_COMMAND.equals(command)) {
					if (hash == null) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'hash' is required");
						continue;
					}
					if (nonce == null) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'nonce' is required");
						continue;
					}
					if (id == null) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'id' is required");
						continue;
					}
					if (time == 0L) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'time' is required");
						continue;
					}
					if (args.size() < 1) {
						netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" "+RemoteControlLib.CRLF_EXEC_COMMAND+" must include the method to run");
						continue;
					}

					if (! hashWasChecked) {
						if (! rc.validateHash(id, nonce, time, hash)) {
							netOut.println(RemoteControlLib.CRLF_ERROR_RESPONSE+" 'hash' did not validate");
							continue;
						}
						hashWasChecked = true;
					}

					try {
						eventLogger.addEvent("Invoking method:"+args.get(0)+" with PipedReader to connect to, and args:(null for now)");
						// /// block here while the called method does its thing, sending output to netOut
						rc.invokeMethod(args.get(0),netOut, args.toArray());
					} 
					catch (IllegalArgumentException e) {
						eventLogger.addEvent("IllegalArgumentException:"+line+" "+e.getMessage());
						netOut.println(e.getMessage());
					} 
					catch (IllegalAccessException e) {
						eventLogger.addEvent("IllegalAccessException:"+line+" "+e.getMessage());
						netOut.println(e.getMessage());
					} 
					catch (InvocationTargetException e) {
						eventLogger.addEvent("InvocationTargetException:"+line+" "+e.getMessage());
						netOut.println(e.getMessage());
					} 
					catch (RemoteControlException e) {
						eventLogger.addEvent("RemoteControlException:"+line+" "+e.getMessage());
						netOut.println(e.getMessage());
					}
				}
			} // end of try
			catch (IOException ioe) {
				eventLogger.addEvent("SocketServer: IOException on socket listen: " + ioe);
			}
		} // end of while



		eventLogger.addEvent("Server thread closing in and out streams");
		try {
			if (netIn != null)
				netIn.close();
			if (netOut != null)
				netOut.close();
		} 
		catch (IOException e) {
			eventLogger.addEvent("IOException closing network connections:"+e.getMessage());
		}

		eventLogger.addEvent("Server thread closing socket");
		try {
			socket.close();
			eventLogger.addEvent("Socket closed:"+socket.isClosed());
			socket = null;
		}
		catch (IOException e) {
			eventLogger.addEvent("IOException when closing socket:"+e.getMessage());
		}
		// tell the boss i'm gone
		rc.delistServer(this);
		eventLogger.addEvent("CRLFServer exit");
		
		this.rc = null;
		this.socket = null;
	}



	public void shutdown() {
		eventLogger.addEvent("CRLFServer shutting down");
		this.running = false;
	}

	@Override
	public void finalize()
	throws Throwable {
		super.finalize();
		this.running = false;
	}

}