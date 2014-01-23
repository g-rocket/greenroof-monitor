package net.clonecomputers.greenroof;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class GreenroofServer {
	static{
		try {
		    System.out.println("Loading driver...");
		    Class.forName("com.mysql.jdbc.Driver");
		    System.out.println("Driver loaded!");
		} catch (ClassNotFoundException e) {
		    throw new RuntimeException("Cannot find the driver in the classpath!", e);
		}
	}
	
	private String insertString = 
			"INSERT INTO `greenroof-monitor`"+
			"(`timestamp`, `raw timestamp`, `raw value`, `processed value`)"+
			" VALUES ('%s',%d,%d,%.2f);";
	
	private ServerSocket server;
	private ExecutorService exec;
	
	public static void main(String[] args) throws IOException {
		GreenroofServer s = new GreenroofServer();
		try{
			s.start();
			s.run();
		} finally {
			try {
				s.server.close();
			} catch(Throwable t){
				System.err.println("couldn't close server");
				t.printStackTrace();
			}
		}
	}
	
	public void start() throws IOException {
		server = new ServerSocket(55555);
		exec = Executors.newCachedThreadPool();
	}
	
	public void run() throws IOException {
		System.out.println("Starting listening");
		while(true){
			Socket s = null;
			try{
				s = server.accept();
				System.out.println("Recieved Connection");
				long rTime = System.currentTimeMillis();
				exec.execute(new ConnectionProcesser(rTime,s));
			}catch(IOException e){
				
			}finally{
				//s.close();
				//System.out.println("Disconnected");
			}
		}
	}
	
	private class ConnectionProcesser implements Runnable {
		private final long rTime;
		private final Socket socket;
		
		public ConnectionProcesser(long rTime, Socket socket){
			this.rTime = rTime;
			this.socket = socket;
		}
		
		public void run(){
			System.out.println("Processing Data");
			List<SensorReading> readings = readData(socket, rTime);
			System.out.println("raw: " + readings);
			try {
				socket.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			//System.out.println("semi-processed:" + readings);
			insert(readings);
		}
	}
	
	public List<SensorReading> readData(Socket socket, long connectionStartTime){
		List<SensorReading> readings = new LinkedList<SensorReading>();
		BufferedInputStream input;
		try {
			input = new BufferedInputStream(socket.getInputStream());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			boolean stopByteWritten = false;
			while(!socket.isClosed() && !stopByteWritten){
				while(input.available() < 7) Thread.yield();
				byte[] data = new byte[7];
				int length = input.read(data);
				System.out.println("Recieved "+print(data));
				if(length != 7){
					throw new IOException("read wrong number of bytes: " + length);
				}
				if((data[6] & 0x80) != 0x80){
					throw new IOException("bad data: " + print(data));
				}
				if((data[0] & 0x80) != 0x00 ||
				   (data[1] & 0x80) != 0x00 ||
				   (data[2] & 0x80) != 0x00 ||
				   (data[3] & 0x80) != 0x00 ||
				   (data[4] & 0x80) != 0x00 ||
				   (data[5] & 0x80) != 0x00){
					throw new IOException("bad data: " + print(data));
				}
				if((data[0] & 0x40) != 0x00){
					throw new IOException("bad time (too big): " +
							Arrays.toString(data));
				}
				long time = 
						((data[0] & 0x3f) << 25) |
						((data[1] & 0x7f) << 18) |
						((data[2] & 0x7f) << 11) |
						((data[3] & 0x7f) << 04) |
						((data[4] & 0x78) >> 3);
				short reading = (short)(
						((data[4] & 0x07) << 8) |
						((data[5] & 0x7f) << 1) |
						((data[6] & 0x80) >> 6));
				byte id = (byte)(data[6] & 0x3f);
				//System.out.println("read one line: " + "("+time+", "+reading+","+id+")");
				readings.add(new SensorReading(reading, connectionStartTime - time, id));
				//System.out.println("put in map");
				//System.out.printf("data[6] = 0x%x\n",data[6]);
				//System.out.printf("data[6] == 0xff -> %b\n", data[6] == (byte)0xff);
				if(data[6] == (byte)0xff) stopByteWritten = true;
				//System.out.println("socket.isClosed() = " + socket.isClosed() + ", stopByteWritten = " + stopByteWritten);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return readings;
	}
	
	public static String print(byte[] ba){
		StringBuilder sb = new StringBuilder();
		for(byte b: ba) sb.append(String.format("%x,", b));
		return sb.toString();
	}
	
	public void insert(long time, short raw, double processed) {
		insert("CURRENT_TIMESTAMP",time,raw,processed);
	}
	
	public void insert(String timestamp,
			long time, int raw, double processed){
		executeSql(String.format(insertString,timestamp,time,raw,processed));
	}
	
	public String getSqlString(SensorReading r) {
		return String.format(insertString, r.timeString, r.timestamp, r.data, r.processedData);
	}
	
	public void insert(List<SensorReading> readings){
		String[] sqlStrings = new String[readings.size()];
		int i = 0;
		for(SensorReading r: readings){
			sqlStrings[i++] = getSqlString(r);
		}
		executeSqls(sqlStrings);
	}

	public ResultSet executeSql(String command){
		return executeSqls(command)[0];
	}
	
	public ResultSet[] executeSqls(String... commands){
		String url = "jdbc:mysql://localhost:3306/greenroof-monitor";
		String username = "java";
		String password = "nd3fuJpZL9MZ9fHB";
		Connection connection = null;
		try {
		    System.out.println("Connecting database...");
		    connection = DriverManager.getConnection(url, username, password);
		    System.out.println("Database connected!");
		    Statement sql = connection.createStatement();
		    ResultSet[] results = new ResultSet[commands.length];
		    int i = 0;
		    for(String command: commands) {
		    	System.out.println("trying to execute sql: "+command);
		    	if(sql.execute(command)) {
		    		results[i++] = sql.getResultSet();
		    	} else {
		    		i++;
		    	}
		    }
		    return results;
		} catch (SQLException e) {
		    throw new RuntimeException("Cannot connect the database!", e);
		} finally {
		    System.out.println("Closing the connection.");
		    if (connection != null) try { connection.close(); } catch (SQLException ignore) {}
		}
	}
}
