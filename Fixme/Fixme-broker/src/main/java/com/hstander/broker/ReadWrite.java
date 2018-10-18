package com.hstander.broker;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.security.cert.PKIXRevocationChecker.Option;
import java.util.concurrent.Future;

import javax.print.attribute.standard.PrinterLocation;

class ReadWrite
{

	public String red = (char)27 + "[31m";
	public String blue = (char)27 + "[34m";
	public String defaultCl = (char)27 + "[39;49m";

	public String	readFromSoc(Attachment attach) throws Exception
	{
		attach.buffer.clear();
		if (attach.channel.read(attach.buffer).get() == -1)
		{
			System.out.format("Router unavailable, Shuting Down ...%n");
			attach.mainThread.interrupt();
			return("");
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
		attach.buffer.clear();
		Charset cs = Charset.forName("UTF-8");
		byte[] data = msg.getBytes(cs);
		attach.buffer.put(data);
	}

	public void	writeToSoc(Attachment attach) throws Exception
	{
		String msg = getTextFromUser(attach.id);
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

	public String handleMsg(String msg, Attachment attach)
	{
		String[] parts = msg.split("\\|");
		if (parts[0].equalsIgnoreCase("ID"))
		{
			attach.id = Integer.parseInt(parts[1]);
			System.out.println("ID: " + parts[1]);
			return("Id updated");
		}
		else
		{
			if (parts.length == 4)
			{
				System.out.println(blue + "Market " + parts[1] + " : " + parts[2] + defaultCl);
			}
			else if (parts.length == 1)
			{
				System.out.println(red + parts[0] + defaultCl);
			}
			return(msg);
		}
	}

	private String getTextFromUser(int id) throws Exception
	{
		String marketId = this.getMarketIdFromUser();
		String symbol = this.getSymbolFromUser();
		String transaction = this.getTransactionFromUser();
		String price = this.getPriceFromUser();
		String qty = this.getQtyFromUser();
		String msg = marketId + "|" + symbol + "|" + transaction + "|" + price + "|" + qty + "|" + Integer.toString(id);
		int checksum = msg.length() + Integer.parseInt(price);
		msg = msg + "|" + Integer.toString(checksum);
		return msg;
	}

	private String getMarketIdFromUser() throws Exception
	{
		System.out.println("Please enter a market ID:");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getMarketIdFromUser();
		else
		{
			try
			{
				Integer.parseInt(msg);
			}
			catch(NumberFormatException e)
			{
				System.out.println(red + "Invalid ID format:" + defaultCl);
				msg = this.getMarketIdFromUser();
			}
		}
		return msg;
	}

	private String getSymbolFromUser() throws Exception
	{
		System.out.println("Please enter a Symbol:");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getSymbolFromUser();
		return msg;
	}

	private String getTransactionFromUser() throws Exception
	{
		System.out.println("Please Select:\n1.Buy\n2.Sell");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getTransactionFromUser();
		try
		{
			int option = Integer.parseInt(msg);
			switch (option){
				case 1:
					msg = "Buy";
					break;
				case 2:
					msg = "Sell";
					break;
				default:
					System.out.println(red + "Invalid Option:" + defaultCl);
					msg = this.getTransactionFromUser();
					break;
			}
		}
		catch(NumberFormatException exc)
		{
			System.out.println(red + "Invalid Option:" + defaultCl);
			msg = this.getTransactionFromUser();
		}
		
		return msg;
	}

	private String getPriceFromUser() throws Exception
	{
		System.out.println("Please enter a Price:");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getPriceFromUser();
		try
		{
			Integer price = Integer.parseInt(msg);
			if (price <= 0)
			{
				System.out.println(red + "Invalid Price:" + defaultCl);
				msg = this.getPriceFromUser();
			}
		}
		catch(NumberFormatException exc)
		{
			System.out.println(red + "Invalid Price:" + defaultCl);
			msg = this.getPriceFromUser();
		}
		return msg;
	}

	private String getQtyFromUser() throws Exception
	{
		System.out.println("Please enter a Quantity:");
		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		String msg = consoleReader.readLine();
		if (msg.length() == 0)
			msg = this.getQtyFromUser();
		try
		{
			Integer qty = Integer.parseInt(msg);
			if (qty <= 0)
			{
				System.out.println(red + "Invalid Quantity:" + defaultCl);
				msg = this.getQtyFromUser();
			}
		}
		catch(NumberFormatException exc)
		{
			System.out.println(red + "Invalid Quantity:" + defaultCl);
			msg = this.getQtyFromUser();
		}
		return msg;
	}
}