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

class ReadWrite
{
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
		attach.buffer.flip();
	}

	public String handleMsg(String msg, Attachment attach) throws Exception
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
			String returnMsg = parts[5] + "|" + attach.id + "|";
			String symbolInfo = symbolInfo(parts[1]);
			JSONObject obj = new JSONObject(symbolInfo);
			try
			{
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
			}
			catch(JSONException e)
			{
				returnMsg += "Symbol Not available in Market";
			}
			int checksum = returnMsg.length() + 22;
			returnMsg += "|" + Integer.toString(checksum);
			return(returnMsg);
		}
	}

	public void	writeToSoc(Attachment attach, String msg) throws Exception
	{
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
}