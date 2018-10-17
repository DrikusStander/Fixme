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
			// String host = "localhost";
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
			// Thread.currentThread().join();
		}
		catch(Exception e)
		{

		}
	}

	private void runMarket()
	{
		System.out.println("runMarket()");
		try
		{
			Attachment attachment = new Attachment();
			attachment.id = _id;
			attachment.server = marketServer;
			marketServer.accept(attachment, new MarketConnectionHandler());
			// Thread.currentThread().join();
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
		// attach.buffer.flip();
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
				// attachment.isRead = false; // It is a write
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
				if (msg.length() > 0)
				{
					/*
					 *	Write to the Market here that was received from the broker
					 *	at the moment just write the message back to the broker
					 */
					String[] parts = msg.split("\\|");

					//******************************************

					int marketID = Integer.parseInt(parts[0]);
					int price = Integer.parseInt(parts[3]);
					int checksum = Integer.parseInt(parts[6]);
					int msglen = parts[0].length() + parts[1].length() + parts[2].length() + parts[3].length() + parts[4].length() + parts[5].length() + 5;
					if (checksum - price == msglen)
					{
						this.writeToMarket(msg, marketID);
					}

					//******************************************


					attachment.buffer.clear();
					byte[] data = msg.getBytes(cs);
					attachment.buffer.put(data);
					attachment.buffer.flip();
					attachment.isRead = false; // It is a write
					// attachment.client.write(attachment.buffer, attachment, this);
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

		private int		writeToMarket(String msg, int marketID)
		{
			System.out.println("MarketID: " +  Integer.toString(marketID));
			Attachment market = markets.get(marketID);
			System.out.println("MarketID: " + Integer.toString(market.id));
			Charset cs = Charset.forName("UTF-8");
			byte[] data = msg.getBytes(cs);
			market.buffer.clear();
			market.buffer.put(data);
			market.buffer.flip();
			market.client.write(market.buffer);
			return(1);
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
			// if (msg.length() > 0)
			// {

				String[] parts = msg.split("\\|");

				//******************************************


				int brokerID = Integer.parseInt(parts[0]);
				int checksum = Integer.parseInt(parts[3]);
				int msglen = parts[0].length() + parts[1].length() + parts[2].length() + 2;
				if (checksum - 22 == msglen)
				{
					this.writeToBroker(msg, brokerID);
				}

				//******************************************

				System.out.println("-----------------------------> " + msg.length());
				attachment.buffer.clear();
				byte[] data = msg.getBytes(cs);
				attachment.buffer.put(data);
				attachment.buffer.flip();
				attachment.isRead = false;
				// attachment.client.write(attachment.buffer, attachment, this);
				Router.clearBuffer(attachment);
			// }
		}
		else 
		{
			System.out.println("-----------------------------> reading");
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

	private int		writeToBroker(String msg, int brokerID)
	{
		System.out.println("BrokerID: " +  Integer.toString(brokerID));
		Attachment broker = brokers.get(brokerID);
		System.out.println("MarketID: " + Integer.toString(broker.id));
		Charset cs = Charset.forName("UTF-8");
		byte[] data = msg.getBytes(cs);
		broker.buffer.clear();
		broker.buffer.put(data);
		broker.buffer.flip();
		broker.client.write(broker.buffer);
		return(1);
	}
}
}