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

public class BrokerPort implements Runnable
{
	private static int _id;

	@Override
	public void run()
	{
		try
		{
			_id = 100000;
			AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
			String host = "localhost";
			int port = 5000;
			InetSocketAddress sAddr = new InetSocketAddress(host, port);
			server.bind(sAddr);	
			System.out.format("Server is listening for Brokers at %s%n", sAddr);
			Attachment attachment = new Attachment();
			attachment.id = _id;
			attachment.server = server;
			server.accept(attachment, new ConnectionHandler());
			Thread.currentThread().join();
		}
		catch(Exception e)
		{

		}
	}

	// public BrokerPort() throws Exception
	// {
	// 	_id = 100000;
	// 	AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
	// 	String host = "localhost";
	// 	int port = 5000;
	// 	InetSocketAddress sAddr = new InetSocketAddress(host, port);
	// 	server.bind(sAddr);	
	// 	System.out.format("Server is listening for Brokers at %s%n", sAddr);
	// 	Attachment attachment = new Attachment();
	// 	attachment.id = _id;
	// 	attachment.server = server;
	// 	server.accept(attachment, new ConnectionHandler());
	// 	Thread.currentThread().join();
	// }

	public static int getId()
	{
		_id++;
		return(_id);
	}


private class Attachment 
{
	int id = 100000;
	AsynchronousServerSocketChannel server;
	AsynchronousSocketChannel client;
	ByteBuffer buffer;
	SocketAddress clientAddr;
	boolean isRead;
}

private class ConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Attachment>
{
	@Override
	public void completed(AsynchronousSocketChannel client, Attachment attachment)
	{
		try
		{
			SocketAddress clientAddr = client.getRemoteAddress();
			System.out.format("Accepted a connection from %s%n", clientAddr);

			attachment.server.accept(attachment, this);
			ReadWriteHandler rwHandler = new ReadWriteHandler();
			Attachment newAttach = new Attachment();
			newAttach.id = BrokerPort.getId();
			newAttach.server = attachment.server;
			newAttach.client = client;
			newAttach.buffer = ByteBuffer.allocate(2048);
			newAttach.isRead = false;
			newAttach.clientAddr = clientAddr;
			
			// client.read(newAttach.buffer, newAttach, rwHandler);

			// int limits = attachment.buffer.limit();
			// byte bytes[] = new byte[limits];
			Charset cs = Charset.forName("UTF-8");
			// String msg = new String(bytes, cs);
			String msg = "ID|" + Integer.toString(newAttach.id);
			newAttach.buffer.clear();
			byte[] data = msg.getBytes(cs);
			newAttach.buffer.put(data);
			newAttach.buffer.flip();
			// attachment.isRead = false; // It is a write
			newAttach.client.write(newAttach.buffer, newAttach, rwHandler);
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

private class ReadWriteHandler implements CompletionHandler<Integer, Attachment>
{
	@Override
	public void completed(Integer result, Attachment attachment)
	{
		System.out.println("------------> In Completion Handler");

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
			System.out.println("------------> Read == true");

			attachment.buffer.flip();
			System.out.println("------------> Fliping the buff");
			int limits = attachment.buffer.limit();
			byte bytes[] = new byte[limits];
			System.out.println("------------> Get data from the buff");
			attachment.buffer.get(bytes, 0, limits);
			Charset cs = Charset.forName("UTF-8");
			String msg = new String(bytes, cs);
			System.out.format("Broker at %s ID %s says: %s%n", attachment.clientAddr, attachment.id, msg);

			attachment.isRead = false; // It is a write
			attachment.buffer.clear();

/* 
			create Block here so router waits for action from the market to reply to the broker
*/


			msg = "OK";
			attachment.buffer.clear();
			byte[] data = msg.getBytes(cs);
			attachment.buffer.put(data);
			attachment.buffer.flip();
			attachment.isRead = false; // It is a write
			System.out.println("------------> Reading from buff writing to socket");
			attachment.client.write(attachment.buffer, attachment, this);

		} 
		else 
		{
			attachment.isRead = true;
			attachment.buffer.clear();
			System.out.println("------------> Reading from socket into buff");
			attachment.client.read(attachment.buffer, attachment, this);
			System.out.println("------------> Done Reading from socket into buff");

		}
	}

	@Override
	public void failed(Throwable e, Attachment attachment)
	{
		e.printStackTrace();
	}
}
}