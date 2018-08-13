package com.hstander.market;

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

public class Main 
{
	int readCount = 0;
    public static void main( String[] args ) throws Exception
    {
		AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
		SocketAddress serverAddr = new InetSocketAddress("localhost", 5001);
		Future<Void> result = channel.connect(serverAddr);
		result.get();
		System.out.println("Connected");
		Attachment attach = new Attachment();
		attach.channel = channel;
		attach.buffer = ByteBuffer.allocate(2048);
		attach.isRead = true;
		attach.mainThread = Thread.currentThread();
	
		// Charset cs = Charset.forName("UTF-8");
		// String msg = "Connection Request";
		// byte[] data = msg.getBytes(cs);
		// attach.buffer.put(data);
		// attach.buffer.flip();
	
		ReadWriteHandler readWriteHandler = new ReadWriteHandler();
		// channel.write(attach.buffer, attach, readWriteHandler);
		channel.read(attach.buffer, attach, readWriteHandler);

		// attach.mainThread.join();
		while(true)
		{
			if (attach.mainThread.isInterrupted())
				return;
		}
	}

}


private class Attachment 
{
	int id;
	AsynchronousSocketChannel channel;
	ByteBuffer buffer;
	Thread mainThread;
	boolean isRead;
}

private class ReadWriteHandler implements CompletionHandler<Integer, Attachment> 
{
	@Override
	public void completed(Integer result, Attachment attach) 
	{
		System.out.println("In ReadWriteHandler->completed");
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
			try 
			{
				msg = this.getTextFromUser();
				if (msg.equalsIgnoreCase("bye"))
				{
					attach.mainThread.interrupt();
					return;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			if (parts.length > 2)
			{
				System.out.println("parts > 2: length = " + Integer.toString(parts.length));
				System.out.println(msg);				
			}
			else if (parts.length == 2)
			{
				System.out.println("parts ==  2");
				if (parts[0].equalsIgnoreCase("ID"))
					attach.id = Integer.parseInt(parts[1]);

			}
			else
			{
				System.out.println("parts ==  1");
			}
				attach.buffer.clear();
				byte[] data = msg.getBytes(cs);
				attach.buffer.put(data);
				attach.buffer.flip();
				attach.isRead = false; // It is a write
				attach.channel.write(attach.buffer, attach, this);
		}
		else
		{
			attach.isRead = true;
			attach.buffer.clear();
			attach.channel.read(attach.buffer, attach, this);
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
}
