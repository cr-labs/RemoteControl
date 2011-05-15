package com.challengeandresponse.remotecontrol;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import com.challengeandresponse.configfilereader.*;

/**
 * Loads configuration from a file, and allows changes to many config items programmatically as well.
 * Some defaults are hard-coded if this object is not initialized by reading a config file.
 * 
 * <pre>
 * Example:
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
 * 
 * 
 * 
 * @author jim
 */

public class RemoteControlConfig {

	//////////////////////////////////////////////
	//////////////// DEFAULTS IF NOT OVERRIDDEN IN A CONFIG FILE
	//////////////////////////////////////////////
	/**
	 * Max number of connections to this server from outside
	 */
	public static final int		MAX_CONNECTIONS = 10;
	public static final int		PORT = 5859;

	public static final int		CACHE_CLEANING_INTERVAL_SEC = 180;
	public static final long	MAX_CLOCK_SKEW_MSEC = 10000;

	
	
	//////////////////////////////////////////////
	//////////////// ELEMENTS IN THE CONFIG FILE
	//////////////////////////////////////////////

	public static final String	CONFIG_ROOT_ELEMENT = 			"com.challengeandresponse.remotecontrol.RemoteControl";

	public static final String 	MAX_CONNECTIONS_ELEMENT = 		"maxconnections";
	public static final String	PORT_ELEMENT =					"port";
	public static final String 	MAX_CLOCK_SKEW_MSEC_ELEMENT =	"maxclockskewmsec";
	
	public static final String	CACHE_CLEANING_INTERVAL_SEC_ELEMENT = "cachecleaningintervalsec";
	
	public static final String	ALLOW_HOST_ELEMENT = "allowhost";
	
	public static final String	CLIENT_SECRET_ELEMENT =		"client";
	public static final String	CLIENT_SECRET_ELEMENT_ID_ATTRIBUTE = "id";
	
	
	////////////
	private int maxConnections;
	private int port;
	private long maxClockSkewMsec;
	private int cacheCleaningIntervalSec;
	
	private ArrayList <InetAddress> allowedHosts;
	private HashMap <String, String> namesToSecrets;


	public RemoteControlConfig() {
		maxConnections = MAX_CONNECTIONS;
		port = PORT;
		cacheCleaningIntervalSec = CACHE_CLEANING_INTERVAL_SEC;
		maxClockSkewMsec = MAX_CLOCK_SKEW_MSEC;
		allowedHosts = new ArrayList<InetAddress>();
		namesToSecrets = new HashMap<String,String> ();
	}
	
	/**
	 * Construct a new SKConfig and load its configuration from a config file's 'configRootElement' section
	 * All defaulting is "true" so that it will not stop with an exception if an element is missing.
	 * The application will have to test values on its own to make sure all mandatory elements were provided in the config file
	 * @param filePath full path to the XML config file
	 * @param configRootElement use the section inside 'configRootElement' instead of the default section name for this config (the constant CONFIG_ROOT_ELEMENT)
	 */
	public RemoteControlConfig(String filePath, String configRootElement)
	throws RemoteControlException, ElementNotFoundException {
		File cfile = new File(filePath);
		try {
			ConfigFileReader cfr = new ConfigFileReader(cfile,configRootElement);
			maxConnections = cfr.getInt(MAX_CONNECTIONS,true,MAX_CONNECTIONS_ELEMENT);
			port = cfr.getInt(PORT,true,PORT_ELEMENT);
			maxClockSkewMsec = cfr.getLong(MAX_CLOCK_SKEW_MSEC,true,MAX_CLOCK_SKEW_MSEC_ELEMENT);
			cacheCleaningIntervalSec = cfr.getInt(CACHE_CLEANING_INTERVAL_SEC,true,CACHE_CLEANING_INTERVAL_SEC_ELEMENT);
			List <String> tempAllowedHosts = cfr.getList(ALLOW_HOST_ELEMENT);
			allowedHosts = new ArrayList <InetAddress>();
			for (String host : tempAllowedHosts) 
				allowedHosts.add(InetAddress.getByName(host));
			namesToSecrets = cfr.getMap(CLIENT_SECRET_ELEMENT, CLIENT_SECRET_ELEMENT_ID_ATTRIBUTE, false);
		}
		catch (ElementNotFoundException e) {
			throw e;
		}
		catch (ConfigFileReaderException e) {
			throw new RemoteControlException(e);
		}
		catch (IOException e) {
			throw new RemoteControlException(e);
		}
	}
	
	
	/**
	 * Construct a new SKConfig and load its configuration from a config file's section whose name is the same as the constant CONFIG_ROOT_ELEMENT
	 * All defaulting is "true" so that it will not stop with an exception if an element is missing.
	 * The application will have to test values on its own to make sure all mandatory elements were provided in the config file
	 * @param filePath full path to the XML config file
	 */
	public RemoteControlConfig(String filePath)
	throws RemoteControlException, ElementNotFoundException {
		this(filePath, CONFIG_ROOT_ELEMENT);
	}

	
	
	
	
	
	
	
	
	//// Getters and Setters
	
	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public long getMaxClockSkewMsec() {
		return maxClockSkewMsec;
	}

	public void setMaxClockSkewMsec(long maxClockSkewMsec) {
		this.maxClockSkewMsec = maxClockSkewMsec;
	}

	public int getCacheCleaningIntervalSec() {
		return cacheCleaningIntervalSec;
	}

	public void setCacheCleaningIntervalSec(int cacheCleaningIntervalSec) {
		this.cacheCleaningIntervalSec = cacheCleaningIntervalSec;
	}

	public void addAllowedHost(InetAddress ia) {
		this.allowedHosts.add(ia);
	}
	
	public boolean isAllowedHost(InetAddress ia) {
		return this.allowedHosts.contains(ia);
	}
	
	
	/**
	 * Given a client ID, return the shared secret for that ID, as set up in the config file. If no secret was found, return null.
	 * @param clientID
	 */
	public String getSecret(String clientID) {
		return namesToSecrets.get(clientID);
	}
	
	public void setSecret(String clientID, String secret) {
		namesToSecrets.put(clientID,secret);
	}
	
	
}
