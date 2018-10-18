package com.hstander.router;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.channels.*;
import java.util.*;

public class Router //implements Runnable
{
	private static int _id;
	private AsynchronousServerSocketChannel brokerServer;
	private AsynchronousServerSocketChannel marketServer;
	private Map<Integer, Attachment> brokers;
	private Map<Integer, Attachment> markets;

	public Router()
	{
		brokers = new HashMap<Integer, Attachment>();
		markets = new HashMap<Integer, Attachment>();
		try
		{
			//Broker Server setup
			_id = 100000;
			brokerServer = AsynchronousServerSocketChannel.open();
			String host = "localhost";
			int brokerPort = 5000;
			InetSocketAddress brokerAddr = new InetSocketAddress(host, brokerPort);
			brokerServer.bind(brokerAddr);
			System.out.format("Server is listening for Brokers at %s%n", brokerAddr);

			this.runBroker();

			//Market Server setup
			marketServer = AsynchronousServerSocketChannel.open();
			int marketPort = 5001;
			InetSocketAddress marketAddr = new InetSocketAddress(host, marketPort);
			marketServer.bind(marketAddr);
			System.out.format("Server is listening for Markets at %s%n", marketAddr);

			this.runMarket();
		}
		catch(Exception e)
		{

		}
	}

	private void runBroker()
	{
		try
		{
			Attachment attachment = new Attachment();
			attachment.id = _id;
			attachment.server = brokerServer;
			brokerServer.accept(attachment, new BrokerConnectionHandler());
		}
		catch(Exception e)
		{

		}
	}

	private void runMarket()
	{
		try
		{
			Attachment attachment = new Attachment();
			attachment.id = _id;
			attachment.server = marketServer;
			marketServer.accept(attachment, new MarketConnectionHandler());
		}
		catch(Exception e)
		{

		}
	}

	public static int getId()
	{
		_id++;
		return(_id);
	}


	private class Attachment
	{
		int id;
		AsynchronousServerSocketChannel server;
		AsynchronousSocketChannel client;
		ByteBuffer buffer;
		SocketAddress clientAddr;
		boolean isRead;
		BrokerReadWriteHandler brokerRwHandler;
		MarketReadWriteHandler marketRwHandler;
	}

	public static void clearBuffer(Attachment attach)
	{
		String msg = "";
		attach.buffer.clear();
		Charset cs = Charset.forName("UTF-8");
		byte[] data = msg.getBytes(cs);
		attach.buffer.put(data);
	}


	public int		writeToMarket(String msg, int marketID)
		{
			try
			{
				Attachment market = markets.get(marketID);
				Charset cs = Charset.forName("UTF-8");
				byte[] data = msg.getBytes(cs);
				market.buffer.clear();
				market.buffer.put(data);
				market.buffer.flip();
				market.client.write(market.buffer);
				clearBuffer(market);
				market.isRead = true;
				try
				{
					market.client.read(market.buffer, market, market.marketRwHandler);
				}
				catch(ReadPendingException e)
				{
					
				}
			}
			catch(NullPointerException e)
			{
				return(0);
			}
			return(1);
		}

		public int		writeToBroker(String msg, int brokerID)
		{
			try
			{
				Attachment broker = brokers.get(brokerID);
				Charset cs = Charset.forName("UTF-8");
				byte[] data = msg.getBytes(cs);
				broker.buffer.clear();
				broker.buffer.put(data);
				broker.buffer.flip();
				broker.client.write(broker.buffer);
				clearBuffer(broker);
				broker.isRead = true;
				try
				{
					broker.client.read(broker.buffer, broker, broker.brokerRwHandler);
				}
				catch(ReadPendingException e)
				{
					System.out.println("readPending");
				}
			}
			catch(NullPointerException e)
			{
				return(0);
			}
			return(1);
		}

	private class BrokerConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Attachment>
	{
		@Override
		public void completed(AsynchronousSocketChannel client, Attachment attachment)
		{
			try
			{
				SocketAddress clientAddr = client.getRemoteAddress();
				System.out.format("Accepted a connection from %s%n", clientAddr);
				attachment.server.accept(attachment, this);
				Attachment newAttach = new Attachment();
				newAttach.brokerRwHandler = new BrokerReadWriteHandler();
				newAttach.id = Router.getId();
				newAttach.server = attachment.server;
				newAttach.client = client;
				newAttach.buffer = ByteBuffer.allocate(2048);
				newAttach.isRead = true;
				newAttach.clientAddr = clientAddr;
				Charset cs = Charset.forName("UTF-8");
				String msg = "ID|" + Integer.toString(newAttach.id);
				newAttach.buffer.clear();
				byte[] data = msg.getBytes(cs);
				newAttach.buffer.put(data);
				newAttach.buffer.flip();
				brokers.put(newAttach.id, newAttach);
				newAttach.client.write(newAttach.buffer);
				Router.clearBuffer(newAttach);
				newAttach.client.read(newAttach.buffer, newAttach, newAttach.brokerRwHandler);
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}

		@Override
		public void failed(Throwable e, Attachment attachment)
		{
			System.out.println("Failed to accept connection!");
			e.printStackTrace();
		}
	}

	private class BrokerReadWriteHandler implements CompletionHandler<Integer, Attachment>
	{
		@Override
		public void completed(Integer result, Attachment attachment)
		{
			if (result == -1)
			{
				try 
				{
					attachment.client.close();
					System.out.format("Stopped listening to the Broker %s ID %s%n", attachment.clientAddr, attachment.id);
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
				}
				return;
			}
			if (attachment.isRead)
			{
				attachment.buffer.flip();
				int limits = attachment.buffer.limit();
				byte bytes[] = new byte[limits];
				attachment.buffer.get(bytes, 0, limits);
				Charset cs = Charset.forName("UTF-8");
				String msg = new String(bytes, cs);
				System.out.format("Broker at %s ID %s says: %s%n", attachment.clientAddr, attachment.id, msg);
				String[] parts = msg.split("\\|");
				if (parts.length == 7)
				{
					int marketID = Integer.parseInt(parts[0]);
					int price = Integer.parseInt(parts[3]);
					int checksum = Integer.parseInt(parts[6]);
					int msglen = parts[0].length() + parts[1].length() + parts[2].length() + parts[3].length() + parts[4].length() + parts[5].length() + 5;
					if (checksum - price == msglen)
					{
						if (writeToMarket(msg, marketID) == 0)
						{
							String newMsg = "Market: " + parts[0] + " Does not exist";
							writeToBroker(newMsg, Integer.parseInt(parts[5]));
							return;
						}
					}
					attachment.buffer.clear();
					byte[] data = msg.getBytes(cs);
					attachment.buffer.put(data);
					attachment.buffer.flip();
					attachment.isRead = false; // It is a write
					Router.clearBuffer(attachment);
				}
			}
			else 
			{
				attachment.isRead = true;
				attachment.buffer.clear();
				attachment.client.read(attachment.buffer, attachment, this);
			}
		}

		

		

		@Override
		public void failed(Throwable e, Attachment attachment)
		{
			e.printStackTrace();
		}
	}

	private class MarketConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Attachment>
{
	@Override
	public void completed(AsynchronousSocketChannel client, Attachment attachment)
	{
		try
		{
			SocketAddress clientAddr = client.getRemoteAddress();
			System.out.format("Accepted a connection from %s%n", clientAddr);
			attachment.server.accept(attachment, this);
			Attachment newAttach = new Attachment();
			newAttach.marketRwHandler = new MarketReadWriteHandler();
			newAttach.id = Router.getId();
			newAttach.server = attachment.server;
			newAttach.client = client;
			newAttach.buffer = ByteBuffer.allocate(2048);
			newAttach.isRead = false;
			newAttach.clientAddr = clientAddr;
			Charset cs = Charset.forName("UTF-8");
			String msg = "ID|" + Integer.toString(newAttach.id);
			newAttach.buffer.clear();
			byte[] data = msg.getBytes(cs);
			newAttach.buffer.put(data);
			newAttach.buffer.flip();
			markets.put(newAttach.id, newAttach);
			newAttach.client.write(newAttach.buffer);
			Router.clearBuffer(newAttach);
			newAttach.client.read(newAttach.buffer, newAttach, newAttach.marketRwHandler);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void failed(Throwable e, Attachment attachment)
	{
		System.out.println("Failed to accept connection!");
		e.printStackTrace();
	}
}

private class MarketReadWriteHandler implements CompletionHandler<Integer, Attachment>
{
	@Override
	public void completed(Integer result, Attachment attachment)
	{
		if (result == -1)
		{
			try
			{
				attachment.client.close();
				System.out.format("Stopped listening to the Market %s ID %s%n", attachment.clientAddr, attachment.id);
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
			return;
		}
		
		if (attachment.isRead)
		{
			attachment.buffer.flip();
			int limits = attachment.buffer.limit();
			byte bytes[] = new byte[limits];
			attachment.buffer.get(bytes, 0, limits);
			Charset cs = Charset.forName("UTF-8");
			String msg = new String(bytes, cs);
			System.out.format("Market at %s ID %s says: %s%n", attachment.clientAddr, attachment.id, msg);
			String[] parts = msg.split("\\|");
			if (parts.length == 4)
			{
				int brokerID = Integer.parseInt(parts[0]);
				int checksum = Integer.parseInt(parts[3]);
				int msglen = parts[0].length() + parts[1].length() + parts[2].length() + 2;
				if (checksum - 22 == msglen)
				{
					if (writeToBroker(msg, brokerID) == 0)
					{
						String newMsg = "Broker: " + parts[0] + " Does not exist";
						writeToMarket(newMsg, Integer.parseInt(parts[1]));
					}
				}
				attachment.buffer.clear();
				byte[] data = msg.getBytes(cs);
				attachment.buffer.put(data);
				attachment.buffer.flip();
				attachment.isRead = false;
				Router.clearBuffer(attachment);
			}
		}
		else 
		{
			attachment.isRead = true;
			attachment.buffer.clear();
			attachment.client.read(attachment.buffer, attachment, this);
		}
	}

	@Override
	public void failed(Throwable e, Attachment attachment)
	{
		e.printStackTrace();
	}

	
}
}