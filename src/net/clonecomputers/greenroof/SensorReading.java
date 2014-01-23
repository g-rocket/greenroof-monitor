package net.clonecomputers.greenroof;

import java.text.*;
import java.util.*;

public class SensorReading {
	public final short data;
	public final long timestamp;
	public final byte sensorID; // low 6 bits
	public final double processedData;
	public final String timeString;
	
	private static DateFormat dateFormat =
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	/**
	 * 
	 * @param data 12-bit sensor data
	 * @param timestamp 32-bit unsigned int
	 * @param sensorID low 6 bits only
	 */
	public SensorReading(short data, long timestamp, byte sensorID) {
		this.data = data;
		this.timestamp = timestamp;
		this.sensorID = sensorID;
		this.processedData = processData(data,sensorID);
		timeString = dateFormat.format(new Date(timestamp));
	}
	
	public String toString() {
		return String.format("(%d,%d,%d,%.4f)",data,timestamp,sensorID,processedData);
	}
	
	private static final double etape_m = -1/40D, etape_b = 16;
	private static final double ord_b = .0908, ord_e = 5.1327;
	private static final double other_resistor = 1000; // ohms
	private static final double v_in = 3.3; // volts
	
	public static double processData(short rawData, byte sensorID){
		double v_out = rawData*(v_in/1024D); // volts
		double resistance = (other_resistor*((v_in/v_out) - 1)); // ohms
		double levelI = etape_m*resistance + etape_b; // inches
		double levelF = levelI/12D; // feet
		double rate = ord_b*Math.exp(levelF*ord_e); //   gallons / minuite
		return rate;
	}
}
