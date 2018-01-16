package util;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for timing experiments
 * @author Javier Barbero Gómez
 */
public class Stopwatch {
	/** Start and stop time (relative) */
	private long start, stop;
	
	/** Is the stopwatch running? */
	private boolean started;
	
	/** Has the stopwatch been executed? (does it contain valid values?) */
	private boolean executed;
	
	/** Partial times */
	private List<Long> laps;
	
	/** Default constructor */
	public Stopwatch(){
		started = false;
		executed = false;
		laps = new ArrayList<Long>();
	}
	
	/** 
	 * Is the stopwatch running?
	 * @return Stopwatch running state
	 */
	public boolean isStarted(){
		return started;
	}
	
	/** Start the stopwatch */
	public void start(){
		if(!isStarted()){
			started = true;
			executed = true;
			laps.clear();
			start = System.nanoTime();
		}
	}
	
	/** Stop the stopwatch */
	public void stop(){
		if(isStarted()){
			stop = System.nanoTime();
			started = false;
		}
	}
	
	/** Register a new lap */
	public void lap(){
		if(isStarted()){
			laps.add(System.nanoTime());
		}
	}
	
	/**
	 * Get the lap time
	 * @param nLap Lap to measure
	 * @return Time of the lap
	 */
	public long lapTime(int nLap){
		return laps.get(nLap) - start;
	}
	
	/**
	 * Total time measured by the stopwatch
	 * @return Last time measured
	 */
	public long elapsed(){
		if(!isStarted() && executed){
			return stop-start;
		}
		else return 0;
	}
	
	/**
	 * Current elapsed time (must be running)
	 * @return Elapsed time since starting the stopwatch
	 */
	public long currentElapsed(){
		if(isStarted())
			return System.nanoTime()-start;
		else return 0;
	}
	
	/** 
	 * Number of laps stored
	 * @return Number of laps
	 */
	public int getNLaps(){
		return laps.size();
	}
}
