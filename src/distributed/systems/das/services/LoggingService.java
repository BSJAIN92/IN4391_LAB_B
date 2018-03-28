package distributed.systems.das.services;

import distributed.systems.das.common.MessageType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LoggingService {
	static String gamelogFile = "GameLog.txt";
	final static Object lock = new Object();
	
	public static void log(MessageType messageType, String text) {
		synchronized(lock) {
			File log = new File(gamelogFile);
			String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
			String s = "[" +timeStamp +"]"+ " [" +messageType.name()+"] "+ text;
			PrintWriter out;
			try {
				out = new PrintWriter(new BufferedWriter(new FileWriter(gamelogFile, true)));
				out.println(s);
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
