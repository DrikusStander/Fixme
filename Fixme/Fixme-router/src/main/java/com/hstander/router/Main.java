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

// import java.util.concurrent;
// import java.lang.Thread;

/**
 * Hello world!
 *
 */
public class Main 
{
	public static void main( String[] args ) throws Exception
	{
		/*
			create Socket server
		*/
		AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();

		/*
			create Socket address with a port number
		*/
		String host = "localhost";
		int port = 8989;
		InetSocketAddress sAddr = new InetSocketAddress(host, port);
		
		/*
			bind the server socket to the address
		*/
		server.bind(sAddr);
		
		System.out.format("Server is listening at %s%n", sAddr);
		
		/*
			create Socket a Attachment and pass the attachment to the completion handler
		*/
		Attachment attachment = new Attachment();
		attachment.server = server;
		server.accept(attachment, new ConnectionHandler());
		
		/*
			pauses the main thread
		*/
		Thread.currentThread().join();
	}

}
class Attachment 
{
	AsynchronousServerSocketChannel server;
	AsynchronousSocketChannel client;
	ByteBuffer buffer;
	SocketAddress clientAddr;
	boolean isRead;
}

class ConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Attachment>
{
	@Override
	public void completed(AsynchronousSocketChannel client, Attachment attachment)
	{
		System.out.println("In ConnectionHandler->completed");
		try
		{
			SocketAddress clientAddr = client.getRemoteAddress();
			System.out.format("Accepted a  connection from  %s%n", clientAddr);

			/*
				accept connections to server on this port
			*/
			attachment.server.accept(attachment, this);

			ReadWriteHandler rwHandler = new ReadWriteHandler();
			Attachment newAttach = new Attachment();
			newAttach.server = attachment.server;
			newAttach.client = client;
			newAttach.buffer = ByteBuffer.allocate(2048);
			newAttach.isRead = true;
			newAttach.clientAddr = clientAddr;
			
			/*
				read from socket into buffer, completion handeler handeles the data in the buffer
			*/
			client.read(newAttach.buffer, newAttach, rwHandler);
			
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

class ReadWriteHandler implements CompletionHandler<Integer, Attachment>
{
	@Override
	public void completed(Integer result, Attachment attachment)
	{
		System.out.println("In ReadWriteHandler->completed Result = " + result);
		if (result == -1) 
		{
			try 
			{
				attachment.client.close();
				System.out.format("Stopped   listening to the   client %s%n", attachment.clientAddr);
			}
			catch (IOException ex) 
			{
				ex.printStackTrace();
			}
			return;
		}
		
		if (attachment.isRead)
		{
			System.out.println("isRead == true");

			//read from the client
			/*
				flips the buffer to the beggining of the buffer
			*/
			attachment.buffer.flip();

			int limits = attachment.buffer.limit();
			byte bytes[] = new byte[limits];

			/*
				transfers data from buffer to array of size limits
			*/
			attachment.buffer.get(bytes, 0, limits);

			Charset cs = Charset.forName("UTF-8");
			String msg = new String(bytes, cs);
			System.out.format("Client at  %s  says: %s%n", attachment.clientAddr, msg);

			attachment.isRead = false; // It is a write
			attachment.buffer.clear();

			// msg = "Message Received";
			// attachment.buffer.clear();
			// byte[] data = msg.getBytes(cs);
			// attachment.buffer.put(data);
			// attachment.buffer.flip();
			// attachment.isRead = false; // It is a write
			// attachment.client.write(attachment.buffer, attachment, this);

		} 
		else 
		{
			System.out.println("isRead == false");
			// Write to the client
			attachment.client.write(attachment.buffer, attachment, this);
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
