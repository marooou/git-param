package org.jenkinsci.plugins.gitparam.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version implements Comparable<Version> {

	private static final String VER_REGEX = "\\d+(\\.\\d+){0,3}";
	private static Pattern verPattern = Pattern.compile(VER_REGEX);
	
	public int[] version;
	
	private int tokenCount;
	private String versionFormat;
	
	public int getMajor() {
		return version[0];
	}


	public int getMinor() {
		return version[1];
	}


	public int getBuild() {
		return version[2];
	}


	public int getRevision() {
		return version[3];
	}
	
	public Version(String ver) {
		Matcher matcher = verPattern.matcher(ver);
		if (matcher.matches()) {
			String matchedStr = matcher.group(0);
			String[] tokens = matchedStr.split("\\.");
			this.tokenCount = tokens.length;
			this.version = new int[4];
			for(int partNo=0; partNo<4; partNo++) {
				setVersionPart(tokens, partNo);
			}
		}
		else {
			throw new IllegalArgumentException("Invalid version format");
		}
	}
	
	private void setVersionPart(String[] tokens, int pos) {
		int val = 0;
		if (pos < tokens.length) {
			val = Integer.parseInt(tokens[pos]);
		}
		this.version[pos] = val;
	}
	
	
	public int compareTo(Version o) {
		for(int partNo=0; partNo<4; partNo++) {
			if (this.version[partNo] != o.version[partNo]) {
				return this.version[partNo] - o.version[partNo];
			}
		}
		return 0;
	}
	
	@Override
	public String toString() {
		String format = getVersionFormatString();
		return String.format(format, getMajor(), getMinor(), getBuild(), getRevision());
	}
	
	private String getVersionFormatString() {
		if (versionFormat == null) {
			versionFormat = String.format(String.format("%%%%d%%0%dd", tokenCount - 1), 0).replace("0", ".%d");
		}
		return versionFormat;
	}
}