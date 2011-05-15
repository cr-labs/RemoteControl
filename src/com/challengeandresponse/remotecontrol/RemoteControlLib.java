package com.challengeandresponse.remotecontrol;

/**
 * Standard library used by both RemoteControl server and RemoteControl
 * clients to standardize the commands given, and algorithms such as
 * the signature algorithm (presently SHA-1 including a nonce and a shared secret)
 * 
 * @author jim
 *
 */
public class RemoteControlLib {
	
	public static final String CRLF_CONNECTION_OPEN = "CONNECTED";
	public static final String CRLF_CONNECTION_CLOSED = "DISCONNECT";
	
	public static final String CRLF_DISCONNECT_COMMAND = "."; // close connection and disconnect
	public static final String CRLF_LIST_COMMANDS_COMMAND = "?"; // list all commands
	public static final String CRLF_EXEC_COMMAND = "#"; // if received, process all the values sent, authenticate, and call the method
	
	public static final String CRLF_ID_COMMAND = "id";
	public static final String CRLF_NONCE_COMMAND = "nonce";
	public static final String CRLF_HASH_COMMAND = "hash";
	public static final String CRLF_TIME_COMMAND = "time";
	
	public static final String CRLF_ERROR_RESPONSE = "ERROR";
	
	private static final String DELIM = " ";

	/**
	 * Make a standard "ready to hash" string to be fed to the makeSignature() method to produce the hash that authenticates the message
	 * @param id
	 * @param nonce
	 * @param time output of System.currentTimeMillis() typically
	 * @return a standard signable string consisting of hard-coded delimiter + id + delimiter + nonce + demimiter + time + delimiter
	 */
	public static final String makeSignableString (String id, String nonce, long time){
		return DELIM + id + DELIM+ nonce + DELIM + time + DELIM;
	}
	
	/**
	 * Make the hash. Tack on the secret before generating. Hash algo is a homebrew SHA-1 with a good pedigree at present. It's sufficient.
	 * @param signableString
	 * @param secret
	 * @return the SHA1 hash of 'signablestring' + delimiter + secret + delimiter
	 */
	public static final String generateSecureHash(String signableString, String secret) {
		return SHA1.encode(signableString+DELIM+secret+DELIM);
	}
	
	
	
	
	/**
	 * For testing
	 * @param args
	 */
	public static void main(String [] args) {
		String signable = RemoteControlLib.makeSignableString("me","nonce",System.currentTimeMillis());
		String hash = RemoteControlLib.generateSecureHash(signable,"aBigSecret");
		System.out.println("signable:"+signable);
		System.out.println("hash:"+hash);
	}
	
	
}
