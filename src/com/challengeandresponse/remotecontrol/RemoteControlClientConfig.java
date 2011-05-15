package com.challengeandresponse.remotecontrol;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import com.challengeandresponse.configfilereader.*;

/**
 * Configuration loader/data structure for the RemoteControlClient.
 * Also suitable for use by applications that /are/ clients to a RemoteControl host.
 * This class encapsulates the identity of the client, as well as all the info needed to connect to the controlled server,
 * such as its hostname and port number, and the secret shared by the client and host.
 *  
 * 
 * <pre>
 * Example:
 * &lt;config&gt;
 * &lt;com.challengeandresponse.remotecontrol.RemoteControlClient&gt;
 *  &lt;remote&gt;
 *   &lt;label&gt;localhost&lt;/label&gt;
 *   &lt;host&gt;127.0.0.1&lt;/host&gt;
 *   &lt;port&gt;5859&lt;/port&gt;
 *   &lt;id&gt;jim&lt;/id&gt;
 *   &lt;secret&gt;j3334323m&lt;/secret&gt;
 * &lt;/remote&gt;
 * &lt;/com.challengeandresponse.remotecontrol.RemoteControlClient&gt;
 * &lt;/config&gt;
 * </pre>
 * 
 * 
 * 
 * @author jim
 */

public class RemoteControlClientConfig {

		//////////////////////////////////////////////
	//////////////// ELEMENTS IN THE CONFIG FILE
	//////////////////////////////////////////////

	public static final String	CONFIG_ROOT_ELEMENT =	"com.challengeandresponse.remotecontrol.RemoteControlClient";

	public static final String 	REMOTE_ELEMENT =	"remote";
	public static final String	LABEL_ELEMENT =		"label";
	public static final String	HOST_ELEMENT =		"host";
	public static final String	PORT_ELEMENT =		"port";
	public static final String	ID_ELEMENT =		"id";
	public static final String	SECRET_ELEMENT = 	"secret";
		
	
	////////////
	
	private HashMap<String,ClientConfig> clientConfigs;
	

	public RemoteControlClientConfig() {
		clientConfigs = new HashMap<String,ClientConfig>();
	}
	
	/**
	 * Construct a new RemoteControlConfig and load its configuration from a config file's section whose name is 'configRootElement' rather than the default that's hardcoded into the constant CONFIG_ROOT_ELEMENT
	 * @param filePath full path to the XML config file
	 * @param configRootElement use the section inside 'configRootElement' instead of the default section name for this config (the constant CONFIG_ROOT_ELEMENT)
	 */
	public RemoteControlClientConfig(String filePath, String configRootElement)
	throws ElementNotFoundException, RemoteControlException {
		this();
		File cfile = new File(filePath);
		try {
			ConfigFileReader cfr = new ConfigFileReader(cfile,configRootElement);
			cfr.stepInto(REMOTE_ELEMENT);
			while (cfr.hasNext()) {
				cfr.stepToNext();
				ClientConfig cc = new ClientConfig();
				String tempHost = cfr.getString(HOST_ELEMENT);
				try {
					cc.host = InetAddress.getByName(tempHost);
				} 
				catch (UnknownHostException e) {
					throw new RemoteControlException("Unknown host in configuration file:"+tempHost);
				}
				cc.port = cfr.getInt(PORT_ELEMENT);
				cc.id = cfr.getString(ID_ELEMENT);
				cc.secret = cfr.getString(SECRET_ELEMENT);
				String tempLabel = cfr.getString(LABEL_ELEMENT);
				if (tempLabel.equals(RemoteControlLib.CRLF_DISCONNECT_COMMAND))
					throw new RemoteControlException("Label cannot be '.' which is a reserved symbol");
				clientConfigs.put(tempLabel,cc);
			}
			cfr.stepInto(null); // reset the pointer
		}
		catch (ConfigFileReaderException e) {
			throw new RemoteControlException("ConfigFileReaderException when loading RemoteControlClient config from: "+filePath+" :"+e.getMessage());
		}
		catch (IOException e) {
			throw new RemoteControlException("IOException when loading RemoteControlClient config from: "+filePath+" :"+e.getMessage());
		}
	}
	
	
	/**
	 * Construct a new RemoteControlConfig and load its configuration from a config file's section whose name is the same as the constant CONFIG_ROOT_ELEMENT
	 * All defaulting is "true" so that it will not stop with an exception if an element is missing.
	 * The application will have to test values on its own to make sure all mandatory elements were provided in the config file
	 * @param filePath full path to the XML config file
	 */
	public RemoteControlClientConfig(String filePath)
	throws ElementNotFoundException, RemoteControlException {
		this(filePath, CONFIG_ROOT_ELEMENT);
	}
	
	public boolean hasLabel(String label) {
		return clientConfigs.containsKey(label);
	}
	
	public InetAddress getHost(String label) {
		ClientConfig cc =clientConfigs.get(label);
		if (cc == null)
			return null;
		return cc.host;
	}

	public int getPort(String label) {
		ClientConfig cc =clientConfigs.get(label);
		if (cc == null)
			return -1;
		return cc.port;
	}

	public String getID(String label) {
		ClientConfig cc =clientConfigs.get(label);
		if (cc == null)
			return null;
		return cc.id;
	}
	
	public String getSecret(String label) {
		ClientConfig cc =clientConfigs.get(label);
		if (cc == null)
			return null;
		return cc.secret;
	}
	
	
	public List <String> getLabels() {
		ArrayList <String> al = new ArrayList<String>();
		Iterator <String> it = clientConfigs.keySet().iterator();
		while (it.hasNext())
			al.add(it.next());
		return al;
	}
	
	class ClientConfig {
		InetAddress host;
		int port;
		String id;
		String secret;
	}
	
	
		
}
