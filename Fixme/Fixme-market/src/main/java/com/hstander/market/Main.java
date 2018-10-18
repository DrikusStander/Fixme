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

//network and api calls
import java.net.*;
import java.io.DataOutputStream;
import org.json.*;

public class Main 
{
	int readCount = 0;
    public static void main( String[] args ) throws Exception
    {
		try
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
			ReadWrite readWrite = new ReadWrite();
			String msg;
			while(true)
			{
				msg = readWrite.readFromSoc(attach);
				if(msg.length() > 0)
				{
					System.out.println("Received Message: " + msg);
					String returnMsg = readWrite.handleMsg(msg, attach);
					if (!returnMsg.equalsIgnoreCase("Id updated"))
					{
						readWrite.writeToSoc(attach, returnMsg);
					}
					System.out.println();
				}
				if (attach.mainThread.isInterrupted())
					return;
			}
		}
		catch(Exception e)
		{
			System.out.println("Router Not Available");
		}
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