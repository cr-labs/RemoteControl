package com.challengeandresponse.remotecontrol.test;

import java.io.PrintStream;
import java.net.UnknownHostException;

import com.challengeandresponse.eventlogger.StdoutEventLogger;
import com.challengeandresponse.remotecontrol.*;

public class Test
implements Runnable {

	private boolean running;

	public Test() {
	}


	/////// RPC methods
	public void streamText(PrintStream ps, Object[] args) {
		for (int i  = 0; i < 10; i++)
			ps.println("line:"+i);
		ps.println("args, if any:");
		for (int i = 0; i < args.length; i++)
			ps.println(args[i]);
		ps.flush();
	}

	public void shutdown(PrintStream ps, Object[] args) {
		running = false;
	}

	public void sayHello(PrintStream ps, Object[] args) {
		ps.println("Hello");
		ps.flush();
	}


	public void run() {
		running = true;
		while (running) {
			try {
				Thread.sleep(400);
			} 
			catch (InterruptedException e) {
			}
			System.out.println("Test running");
		}
	}

	
	
	public static void main(String[] args)
	throws RemoteControlException, UnknownHostException {

		Test t = new Test();

		RemoteControlConfig rcConfig = new RemoteControlConfig();
		rcConfig.setSecret("jim","jim");

		RemoteControl rc = new RemoteControl(t,rcConfig, new StdoutEventLogger());
		rc.allowHost("0:0:0:0:0:0:0:1");
		rc.allowHost("127.0.0.1");
		rc.registerMethod("sayHello");
		rc.registerMethod("shutdown");
		rc.registerMethod("streamText");

		String[] s = new String[1];
		s[0]="jim";

		Thread tThread = new Thread(t);
		tThread.start();

		Thread rcThread = new Thread(rc);
		rcThread.start();


		try {
			tThread.join();
		} 
		catch (InterruptedException e) {
		}

		rc.shutdown();

	}

}