package org.wikipedia.miner.extract.util;

import java.io.File;
import java.util.*;
import java.util.regex.*;
import org.apache.hadoop.fs.Path;


public class Util {

	public static String normaliseTitle(String title) {
		if ( (title == null) || (title.trim().length() == 0) )
			return title;

		StringBuffer s = new StringBuffer();
		s.append(Character.toUpperCase(title.charAt(0)));
		s.append(title.substring(1).replace('_', ' '));
	
		String newTitle = s.toString();
		int index = newTitle.indexOf('#');
		
		if (index>0)
			newTitle = newTitle.substring(0,index);

		return newTitle.trim();
	}

	public static long getFileSize(Path path) {
		File file = new File(path.toString());
		return file.length();
	}

	/**
	 * Gathers complex regions: ones which can potentially be nested within each other.
	 * 
	 * The returned regions (an array of start and end positions) will be either
	 * non-overlapping or cleanly nested, and sorted by end position. 
	 */ 
	public static List<int[]> gatherComplexRegions(String markup, String prefix, String startRegex, String endRegex) {
		//an array of regions we have identified
		//each region is given as an array containing start and end character indexes of the region. 
		List<int[]> regions = new ArrayList<int[]>() ;

		//a stack of region starting positions
		List<Integer> startStack = new ArrayList<Integer>() ;
		if (prefix == null)
			prefix = "";
		Pattern p = Pattern.compile("("+prefix+"(" + startRegex + ")|(" + endRegex + "))", Pattern.DOTALL) ;
		Matcher m = p.matcher(markup) ;
		
		while(m.find()) {
			Integer p1 = m.start() ;
			Integer p2 = m.end() ;  
			if (m.group(2) != null) {
				//this is the start of an item
				startStack.add(p1) ;
			} else {
				//this is the end of an item
				if (!startStack.isEmpty()) {
					int start = startStack.get(startStack.size()-1) ;
					startStack.remove(startStack.size()-1) ;
					
					int[] region = {start, p2} ;
					regions.add(region) ;

					//print (" - item [region[0],region[1]]: ".substr(markup, region[0], region[1]-region[0])."\n") ;
				} //else {
					//logProblem("oops, we found the end of an item, but have no idea where it started") ;
				//}
			}
		}
		//if (!startStack.isEmpty()) {
			//logProblem("oops, we got to the end of the markup and still have items that have been started but not finished") ;
		//}
		return regions ;
	}
}
