package rs.ac.bg.etf.pp1.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingUtils 
{
	@SuppressWarnings("rawtypes")
	public static Logger getLogger(Class source) 
	{
		System.setProperty("logSource", source.getSimpleName());
		return LogManager.getRootLogger();
	}
	
	public static Logger getLogger()
	{
		System.setProperty("logSource", "mj");
		return LogManager.getRootLogger();
	}
}
