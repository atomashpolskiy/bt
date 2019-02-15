package bt.net;

import java.util.HashMap;
import java.util.ArrayList;
import java.nio.file.*;
import java.io.*;

public class CoverMe {
    /**
       Register branch of id id with some branchCount
    **/
    static void reg(String id, Integer branchCount) {
	try {
	    Files.write(Paths.get(System.getProperty("user.home")+"/"+id+".report"), (Integer.toString(branchCount)+"\n").getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
	}catch (IOException e) {
	    // Nothing to do.
	}
    }
}
