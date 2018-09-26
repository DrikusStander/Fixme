package com.hstander.broker;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.Future;


/*

Sender ID
Buy/Sell
Equaty
Price
Amount
Receiver
Checksum

*/

/**
 * Hello world!
 *
 */
public class Main 
{
    public static void main( String[] args ) throws Exception
    {
		AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
		SocketAddress serverAddr = new InetSocketAddress("localhost", 5000);
		Future<Void> result = channel.connect(serverAddr);
		result.get();
		System.out.println("Connected");
		Attachment attach = new Attachment();
		attach.channel = channel;
		attach.buffer = ByteBuffer.allocate(2048);
		attach.isRead = true;
		attach.mainThread = Thread.currentThread();
		// ReadWriteHandler readWriteHandler = new ReadWriteHandler();
		ReadWrite readWrite = new ReadWrite();

		// channel.read(attach.buffer, attach, readWriteHandler);
		String msg;
		while(true)
		{

			msg = readWrite.readFromSoc(attach);
			if(msg.length() > 0)
			{
				/*
				 * Handle id adressing here
				*/
				System.out.println(msg);
			}
			// readWrite.writeToSoc(attach);

			if (attach.mainThread.isInterrupted())
				return;
		}
	}
}

class ReadWrite
{
	public String	readFromSoc(Attachment attach) throws Exception
	{
		attach.buffer.clear();
		if (attach.channel.read(attach.buffer).get() == -1)
		{
			System.out.format("Router unavailable, Shuting Down ...%n");
			attach.mainThread.interrupt();
			return("Router unavailable, Shuting Down ...%n");
		}
		attach.buffer.flip();
		Charset cs = Charset.forName("UTF-8");
		int limits = attach.buffer.limit();
		byte bytes[] = new byte[limits];
		attach.buffer.get(bytes, 0, limits);
		String msg = new String(bytes, cs);
		clearBuffer(attach);
		return(msg);
	}

	private void clearBuffer(Attachment attach)
	{
		String msg = "";
		System.out.println("######################### " + msg.length());
		attach.buffer.clear();
		Charset cs = Charset.forName("UTF-8");
		byte[] data = msg.getBytes(cs);
		attach.buffer.put(data);
	}

	public void	writeToSoc(Attachment attach) throws Exception
	{
		String msg = getTextFromUser();
		attach.buffer.clear();
		Charset cs = Charset.forName("UTF-8");
		byte[] data = msg.getBytes(cs);
		attach.buffer.put(data);
		attach.buffer.flip();
		if (attach.channel.write(attach.buffer).get() == -1)
		{
			System.out.format("Router unavailable, Shuting Down ...%n");
			attach.mainThread.interrupt();
			return;
		}
	}

	private String getTextFromUser() throws Exception
	{
		/*
		 *	handle user input here and formati into FIX notation to send to market
		 */
		System.out.println("Please enter a  message  (Bye  to quit):");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getTextFromUser();
		return msg;
	}
}

class Attachment 
{
	int id;
	AsynchronousSocketChannel channel;
	ByteBuffer buffer;
	Thread mainThread;
	boolean isRead;
}









class ReadWriteHandler implements CompletionHandler<Integer, Attachment> 
{
	@Override
	public void completed(Integer result, Attachment attach) 
	{
		System.out.println("In ReadWriteHandler->completed");
		if (result == -1) 
		{
			System.out.format("Router unavailable, Shuting Down ...%n");
			attach.mainThread.interrupt();
			return;
		}
		if (attach.isRead)
		{
			attach.buffer.flip();
			Charset cs = Charset.forName("UTF-8");
			int limits = attach.buffer.limit();
			byte bytes[] = new byte[limits];
			attach.buffer.get(bytes, 0, limits);
			String msg = new String(bytes, cs);
			String[] parts = msg.split("\\|");
			for (String temp : parts)
				System.out.println("<--------- " + temp + " ------------->");
			System.out.println("length = " + Integer.toString(parts.length));
			System.out.println("Server Responded: " + msg);

			// if (parts.length > 2)
			// {
			// 	System.out.println("parts > 2: length = " + Integer.toString(parts.length));
			// 	System.out.println(msg);
			// }

			/*
				Do regex match here for first message containing id
			*/
			// if (parts.length == 2)
			// {
			// 	System.out.println("parts ==  2");
			if (parts[0].equalsIgnoreCase("ID"))
			{
				System.out.println("Setting ID");

				attach.id = Integer.parseInt(parts[1]);
				attach.isRead = true;
				attach.buffer.clear();
				msg = "";
				byte[] data = msg.getBytes(cs);
				attach.buffer.put(data);
				attach.buffer.flip();
				attach.channel.read(attach.buffer, attach, this);
			}
			// }
			else
			{
				System.out.println("Else");
				try 
				{
					msg = this.getTextFromUser();
					
					if (msg.equalsIgnoreCase("bye"))
					{
						attach.mainThread.interrupt();
						return;
					}
					attach.buffer.clear();
					byte[] data = msg.getBytes(cs);
					attach.buffer.put(data);
					attach.buffer.flip();
					attach.isRead = false; // It is a write
					attach.channel.write(attach.buffer, attach, this);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			
				
				// 	try
				// {
				// }
				// catch(Exception e)
				// {

				// }
			}
		}
		else
		{
			attach.isRead = true;
			attach.buffer.clear();
			try
			{
				attach.channel.read(attach.buffer, attach, this);
			}
			catch(Exception e)
			{

			}
		}
	}
	
	@Override
	public void failed(Throwable e, Attachment attach)
	{
		e.printStackTrace();
	}

	private String getTextFromUser() throws Exception
	{
		System.out.println("Please enter a  message  (Bye  to quit):");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getTextFromUser();
		return msg;
	}

	
}



