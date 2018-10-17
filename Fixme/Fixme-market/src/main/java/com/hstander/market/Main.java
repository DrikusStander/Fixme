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
	
		ReadWrite readWrite = new ReadWrite();
		String msg;
		while(true)
		{
			msg = readWrite.readFromSoc(attach);
			if(msg.length() > 0)
			{
				System.out.println(msg.length() + " " + msg);
				/*
				 *	mandle message here
				 *		- check if stock is available in this market
				 *			^ check if if it is buy or sell
				 *				+ check if the quantity of that stock item is available if buy
				 *				+ reply with successful/unsucessful buy
				 */
			
				String returnMsg = readWrite.handleMsg(msg, attach);
				System.out.println("returnedMsgL: " + returnMsg);
				if (!returnMsg.equalsIgnoreCase("Id updated"))
				{
					readWrite.writeToSoc(attach, returnMsg);
					// byte[] data2 = new byte[attach.buffer.limit()];
					// attach.buffer.get(data2, 0, attach.buffer.limit());
					// String sss = new String(data2);
					// System.out.println("buffer after write: " + sss);
				}

			}
			// readWrite.handleMsg(msg, attach);
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
			System.out.println("Read from socket: " + msg);
			return(msg);
		}

		private void clearBuffer(Attachment attach)
		{
			String msg = "";
			attach.buffer.clear();
			Charset cs = Charset.forName("UTF-8");
			byte[] data = msg.getBytes(cs);
			attach.buffer.put(data);
			attach.buffer.flip();
		}

		public String handleMsg(String msg, Attachment attach) throws Exception
		{
			String[] parts = msg.split("\\|");
			if (parts[0].equalsIgnoreCase("ID"))
			{
				attach.id = Integer.parseInt(parts[1]);
				return("Id updated");
			}
			else
			{
				String returnMsg = parts[5] + "|" + attach.id + "|";
				/*
				 *	need to handle all parts of the passed string
				 */
				String symbolInfo = symbolInfo(parts[1]);
				JSONObject obj = new JSONObject(symbolInfo);
				String symbolPrice = obj.getJSONObject("Global Quote").getString("05. price");
				String symbolVolume = obj.getJSONObject("Global Quote").getString("06. volume");
				if (parts[2].equalsIgnoreCase("buy"))
				{
					if (Double.parseDouble(parts[3]) >= Double.parseDouble(symbolPrice))
					{
						if (Integer.parseInt(parts[4]) <= Integer.parseInt(symbolVolume))
						{
							returnMsg += "Buy Success";
						}
						else
						returnMsg += "Buy Failed: Insuffiecient Quantity available";
					}
					else
					returnMsg += "Buy Failed: Price to low";
				}
				else
				{
					if (Double.parseDouble(parts[3]) <= Double.parseDouble(symbolPrice))
					{
						returnMsg += "Sell Success";
					}
					else
					returnMsg += "Sell Failed: Price to High";
				}
				int checksum = returnMsg.length() + 22;
				returnMsg += "|" + Integer.toString(checksum);
				// writeToSoc(attach, returnMsg);
				return(returnMsg);
			}
		}

		public void	writeToSoc(Attachment attach, String msg) throws Exception
		{
			System.out.println("in writeToSoc: msg: " + msg);
			
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

		private String symbolInfo(String symbol) throws Exception
		{
			URL url = new URL("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=GSA8L5WLCNAL7YFL");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null)
			{
				content.append(inputLine);
			}
			in.close();
			con.disconnect();
			return(content.toString());
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






	// class ReadWriteHandler implements CompletionHandler<Integer, Attachment> 
	// {
	// 	@Override
	// 	public void completed(Integer result, Attachment attach)
	// 	{
	// 		System.out.println("In ReadWriteHandler->completed");
	// 		if (result == -1)
	// 		{
	// 			System.out.format("Router unavailable, Shuting Down ...%n");
	// 			attach.mainThread.interrupt();
	// 			return;
	// 		}
	// 		if (attach.isRead)
	// 		{
	// 			attach.buffer.flip();
	// 			Charset cs = Charset.forName("UTF-8");
	// 			int limits = attach.buffer.limit();
	// 			byte bytes[] = new byte[limits];
	// 			attach.buffer.get(bytes, 0, limits);
	// 			String msg = new String(bytes, cs);
	// 			String[] parts = msg.split("\\|");
	// 			for (String temp : parts)
	// 				System.out.println("<--------- " + temp + " ------------->");
	// 			System.out.println("length = " + Integer.toString(parts.length));
	// 			System.out.println("Server Responded: " + msg);
				
				
	// 				if (parts[0].equalsIgnoreCase("ID"))
	// 					attach.id = Integer.parseInt(parts[1]);
		
	// 				msg = "Market: ";
					
	// 				attach.buffer.clear();
	// 				byte[] data = msg.getBytes(cs);
	// 				attach.buffer.put(data);
	// 				attach.buffer.flip();
	// 				attach.isRead = false; // It is a write
	// 				attach.channel.write(attach.buffer, attach, this);
	// 			// }
	// 		}
	// 		else
	// 		{
	// 			attach.isRead = true;
	// 			attach.buffer.clear();
	// 			attach.channel.read(attach.buffer, attach, this);
	// 		}
	// 	}
		
	// 	@Override
	// 	public void failed(Throwable e, Attachment attach)
	// 	{
	// 		e.printStackTrace();
	// 	}

	// 	private String getTextFromUser() throws Exception
	// 	{
	// 		System.out.println("Please enter a  message  (Bye  to quit):");
	// 		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
	// 		String msg = consoleReader.readLine();
	// 		if (msg.length() == 0)
	// 			msg = this.getTextFromUser();
	// 		return msg;
	// 	}
	// }
