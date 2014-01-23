package net.clonecomputers.greenroof;

public class SensorReading {
	public final short data;
	public final long timestamp;
	public final byte sensorID; // low 7 bits
	
	/**
	 * 
	 * @param data 12-bit
	 * @param timestamp unsigned int
	 * @param sensorID low 7 bits only
	 */
	public SensorReading(short data, long timestamp, byte sensorID) {
		this.data = data;
		this.timestamp = timestamp;
		this.sensorID = sensorID;
	}
}
