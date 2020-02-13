package com.gnarly.ld;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ServerInfo implements Serializable {

	private static final long serialVersionUID = 1098398424L;

	private static final String DEFAULT_PREFIX = "!";

	public String prefix;
	public Set<String> blacklist;

	public ServerInfo() {
		prefix = DEFAULT_PREFIX;
		blacklist = new HashSet<>();
	}
}
