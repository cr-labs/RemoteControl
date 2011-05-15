package com.challengeandresponse.remotecontrol;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.challengeandresponse.eventlogger.EventLoggerI;
import com.challengeandresponse.timedtokencache.TimedTokenCache;
import com.challengeandresponse.timedtokencache.TimedTokenCacheString;

/*
 * Design note:
 * This class ONLY dispatches connections to Server objects that it spawns.
 * All the heavy lifting is done in Server.
 */

// TODO if startup fails due to e.g. can't register method, app does not terminate -- it should!
// TODO maxConnections config item doesn't do anything right now. all connections are accepted.
// TODO Shutdown is still locking up due to callback from child of this to shutdown method over in caller
// TODO Make this simpler. Make the client just issue one command, receive a response, and terminate.

/**
 * RemoteControl allows over-the-wire control of server apps (such as SK).
 * It also allows apps to stream performance, state, or event data back to listening apps such as a Flash front-end (as with SK).
 * <p>RemoteControl at present provides for basic authentication via a shared secret, for replay detection, and for 
 * limits on controllers based on IP address or host name. AT present it's all TCP over a plain TCP link, but I hope it is generic
 * enough to be retrofitted in the future to be transport-agnostic - supporting web, chat or other interfaces.
 * 
 * <p>
 * RemoteControl has these components:<br />
 * RemoteControl -- this class, for inclusion in server-side apps that are to be controlled<br />
 * RemoteControlConfig -- holds all the configuration data structure that can be set in a config file<br />
 * RemoteControlClient -- command line or interactive Java app for sending commands and receiving responses from a RemoteControl-enabled service. Looks like Telnet but performs login and authentication behind the scenes.<br />
 * RemoteControlClientConfig -- holds configuration data structure for the RemoteControlClient<br />
 * RemoteControlLib -- Java library for apps that want to embed RemoteController functions - hardcodes the commands that clients and server must agree on<br />
 * </p>
 * <p>
 * Methods registered for remote calls must have this signature:<br />
 * public void method(PrintStream ps, Object[] args)<br />
 * </p>
 * 
 * <p>
 * Sample config file. Config file can be skipped, as most values can be set through methods of RemoteControl.<br />
 * Example:<br />
 * <pre>
 * &lt;config&gt;
 * &lt;com.challengeandresponse.remotecontrol.RemoteControl&gt;
 *    &lt;maxconnections&gt;10&lt;/maxconnections&gt;
 *    &lt;port&gt;5859&lt;/port&gt;
 *    &lt;cachecleaningintervalsec&gt;180&lt;/cachecleaningintervalsec&gt;
 *    &lt;maxclockskewmsec&gt;10000&lt;/maxclockskewmsec&gt;
 * 
 *    &lt;allowhost&gt;127.0.0.1&lt;/allowhost&gt;
 *    &lt;allowhost&gt;0:0:0:0:0:0:0:1&lt;/allowhost&gt;
 *    
 *    &lt;client id="jim"&gt;j3334323m&lt;/client&gt;
 *    &lt;client id="client2"&gt;erweri23023usnad1234&lt;/client&gt;
 * &lt;/com.challengeandresponse.remotecontrol.RemoteControl&gt;
 * &lt;/config&gt;
 * </pre>
 * </p>
 * 
 * 
 * TODO Should eventually use TLS for communications, but for now, we just open a plain TCP connection.
 * @author jim  
 *
 */
public class RemoteControl
implements Runnable {

	// list of hosts that are allowed to control this RemoteControl instance
	private TimedTokenCache usedNonces;
	private ConcurrentHashMap <String,Method> methods;

	private Object obj; // remote control methods will be called against methods of this object
	private RemoteControlConfig rcc;
	private EventLoggerI eventLogger;

	private boolean running = false;
	private static final int SOCKET_TIMEOUT_MSEC = 1000;

	// child thread management
	private ThreadGroup serverThreads = null;
	private static final String THREAD_GROUP_NAME = "THREADS";
	private ConcurrentHashMap <CRLFServer,Thread> serverThreadList;

	/**
	 * @param obj the object to call the methods against
	 * @param rcc the RemoteControlConfig with all the settings for this instance in it
	 * @param el an EventLogger to post interesting events to
	 */
	public RemoteControl(Object obj, RemoteControlConfig rcc, EventLoggerI el) {
		this.obj = obj;
		this.rcc = rcc;
		this.eventLogger = el;
		this.running = false;
		usedNonces = new TimedTokenCache();
		methods = new ConcurrentHashMap<String,Method>();

		usedNonces.startCleaner(rcc.getCacheCleaningIntervalSec(),"RemoteControl.usedNonces");
		serverThreads = new ThreadGroup(THREAD_GROUP_NAME);
		serverThreadList = new ConcurrentHashMap <CRLFServer,Thread>();
	}


	/**
	 * Add a host that is allowed to control this instance of RemoteControl
	 * @param host may be either a host name or dotted IP address; will be resolved by InetAddress.getByName() so follow those rules
	 * @throws UnknownHostException if the host cannot be identified via DNS lookup or if the address is not valid
	 */
	public void allowHost(String host)
	throws UnknownHostException {
		InetAddress ia = InetAddress.getByName(host);
		rcc.addAllowedHost(ia);
	}


	/**
	 * Checks for replay of a nonce from a creator. If not found, returns true and adds then nonce to the 'usedNonces' list so that it will be found next time. If found, returns false. The nonce timeout is the same as the cache cleaner interval as set in the Remote Control Config
	 * @param nonce
	 * @param creator
	 * @return true if a nonce is not found (and therefore is a fresh, new nonce); false if the nonce is already listed
	 */
	public boolean checkNonce(String nonce, String creator) {
		TimedTokenCacheString s = new TimedTokenCacheString(nonce+"."+creator);
		return usedNonces.tokenIsUnique(s,System.currentTimeMillis() + ((long)rcc.getCacheCleaningIntervalSec() * 1000));
	}


	private void registerMethod(String methodName, Method m) {
		methods.put(methodName,m);
	}

	public void registerMethod(String methodName)
	throws RemoteControlException {
		Method m;
		try {
			m = obj.getClass().getMethod(methodName, PrintStream.class, Object[].class);
		}
		catch (SecurityException e) {
			throw new RemoteControlException("Cannot register method: "+methodName+"; SecurityException:"+e.getMessage());
		} 
		catch (NoSuchMethodException e) {
			throw new RemoteControlException("Cannot register method: "+methodName+"; NoSuchMethodException:"+e.getMessage());
		}
		registerMethod(methodName,m);
	}

	public void unregisterMethod(String methodName) {
		methods.remove(methodName);
	}

	public List <String> getRegisteredMethods() {
		ArrayList<String> al = new ArrayList<String> ();
		Enumeration <String> en = methods.keys();
		while (en.hasMoreElements())
			al.add(en.nextElement());
		return al;
	}


	public boolean validTime(long checkTime) {
		return (Math.abs(System.currentTimeMillis() - checkTime) <= rcc.getMaxClockSkewMsec());
	}

	public boolean validateHash(String id, String nonce, long time, String offeredHash) {
		if ((id == null) || (nonce == null) || (offeredHash == null))
			return false;
		String secret = rcc.getSecret(id);
		// secret not found
		if (secret == null) {
			eventLogger.addEvent("RemoteControl cannot validate a hash for id:"+id+" because that ID was not found in the clients list");
			return false;
		}
		String hashable = RemoteControlLib.makeSignableString(id, nonce, time);
		String correctHash = RemoteControlLib.generateSecureHash(hashable, secret);
		return (correctHash.equals(offeredHash));
	}



	/// socket listener, and services...
	public void run() {
		eventLogger.addEvent("RemoteControl: starting");
		running = true;
		try {
			ServerSocket listener = new ServerSocket(rcc.getPort());
			listener.setSoTimeout(SOCKET_TIMEOUT_MSEC);

			while (running) {
				Socket connectedSocket = null;
				try {
					connectedSocket = listener.accept();
				}
				catch (java.net.SocketTimeoutException ste) {
					delay(250);
					continue;
				}
				eventLogger.addEvent("Connection attempt on control port from:"+connectedSocket.getInetAddress().getHostAddress());
				if (! rcc.isAllowedHost(connectedSocket.getInetAddress())) {
					connectedSocket.close();
					eventLogger.addEvent("Rejected connection from unauthorized host:"+connectedSocket.getInetAddress().getHostAddress());
					continue;
				}
				eventLogger.addEvent("Accepted connection from authorized host:"+connectedSocket.getInetAddress().getHostAddress());
				CRLFServer server = new CRLFServer(connectedSocket,this,eventLogger);
				Thread t = new Thread(serverThreads,server,"CRLFServer"+System.currentTimeMillis());
				serverThreadList.put(server, t);
				System.out.println(t.getName());
				t.start();
			}
			//			eventLogger.addEvent("RemoteControl terminating.");
			//			usedNonces.stopCleaner();
			//			Enumeration <CRLFServer> serverKeys = serverThreadList.keys();
			//			while (serverKeys.hasMoreElements()) {
			//				CRLFServer server = serverKeys.nextElement();
			//				Thread serverThread = serverThreadList.get(server);
			//				eventLogger.addEvent("RemoteControl:shutting down CRLF child server:"+serverThread.getName());
			//				server.shutdown();
			//				try {
			//					serverThread.join();
			//				} 
			//				catch (InterruptedException e) {
			//				}
			//			}
		} 
		catch (IOException ioe) {
			eventLogger.addEvent("RemoteControl: IOException on socket listen: " + ioe.getMessage());
		} 
	}


	public void delistServer(CRLFServer server) {
		serverThreadList.remove(server);
	}


	public void shutdown() {
		running = false; // start no more instances

		// TODO Experimental
		eventLogger.addEvent("RemoteControl terminating.");
		usedNonces.stopCleaner();
		Enumeration <CRLFServer> serverKeys = serverThreadList.keys();
		while (serverKeys.hasMoreElements()) {
			CRLFServer server = serverKeys.nextElement();
			Thread serverThread = serverThreadList.get(server);
			eventLogger.addEvent("RemoteControl:shutting down CRLF child server:"+serverThread.getName());
			server.shutdown();
			try {
				serverThread.join();
			} 
			catch (InterruptedException e) {
			}
		}
	}





	public void invokeMethod(String methodName, PrintStream ps, Object... args)
	throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, RemoteControlException {
		Method m = methods.get(methodName);
		if (m == null)  {
			eventLogger.addEvent("Method "+methodName+" not found");
			throw new RemoteControlException("Method "+methodName+" not found. Cannot invoke");
		}
		m.invoke(obj,ps,args);
	}





	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		shutdown();
	}

	private void delay(long msec) {
		try {
			Thread.sleep(msec);
		} 
		catch (InterruptedException e) {
		}
	}


}
