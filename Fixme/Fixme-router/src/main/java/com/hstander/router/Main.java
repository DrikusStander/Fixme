package com.hstander.router;

import java.lang.Thread;

public class Main 
{
	public static void main( String[] args ) throws Exception
	{
		Thread brokerThread = new Thread(new BrokerPort());
		Thread marketThread = new Thread(new MarketPort());
		brokerThread.start();
		marketThread.start();

		// BrokerPort brokerPort = new BrokerPort();
		// MarketPort marketPort = new MarketPort();
		// Thread.currentThread().join();
	}

}
