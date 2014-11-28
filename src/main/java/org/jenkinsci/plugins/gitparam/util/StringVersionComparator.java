package org.jenkinsci.plugins.gitparam.util;

import java.util.Comparator;

public class StringVersionComparator implements Comparator<String> {

	private boolean reverse;
	private boolean parseVersion;
	
	public boolean isReverse() {
		return reverse;
	}

	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	public boolean parseVersion() {
		return parseVersion;
	}

	public void parseVersion(boolean parseVersion) {
		this.parseVersion = parseVersion;
	}

	public StringVersionComparator() {
		reverse = false;
		parseVersion = false;
	}
	
	public StringVersionComparator(boolean reverse, boolean parseVersion) {
		this.reverse = reverse;
		this.parseVersion = parseVersion;
	}

	public int compare(String o1, String o2) {
		if (this.parseVersion)
			return this.versionCompare(o1, o2);
		else
			return this.stringCompare(o1, o2);
	}
	
	private int versionCompare(String o1, String o2) {
		Version v1 = null, 
				v2 = null;
		try {
			v1 = new Version(o1);
			v2 = new Version(o2);
			return reverse ? v2.compareTo(v1) : v1.compareTo(v2);
		}
		catch(IllegalArgumentException ex) {
			return stringCompare(o1, o2);
		}
	}
	
	private int stringCompare(String o1, String o2) {
		return reverse ? o2.compareTo(o1) : o1.compareTo(o2);
	}

}
