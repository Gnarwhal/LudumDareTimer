package com.gnarly.ld;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public class LDTimerEventListener extends ListenerAdapter {

	private static final long SECOND = 1000;
	private static final long MINUTE = 60 * SECOND;
	private static final long HOUR   = 60 * MINUTE;
	private static final long DAY    = 24 * HOUR;

	private static final long REFRESH_INTERVAL = 1 * DAY + 0 * HOUR + 0 * MINUTE + 0 * SECOND;

	private Map<String, ServerInfo> servers;

	private long nextRefresh;

	private long start;
	private long compoEnd;
	private long compoSubmissionHour;
	private long jamEnd;
	private long jamSubmissionHour;
	private long rate;
	private long results;

	public LDTimerEventListener() {
		servers = new HashMap<>();
		loadServerInfo();

		var file = (FileInputStream) null;
		try {
			file = new FileInputStream("TimeData.txt");
		} catch (IOException e) {}

		if (file != null) {
			var scanner = new Scanner(file);
			try {
				nextRefresh         = scanner.nextLong(); scanner.nextLine();
				start               = scanner.nextLong(); scanner.nextLine();
				compoEnd            = scanner.nextLong(); scanner.nextLine();
				compoSubmissionHour = scanner.nextLong(); scanner.nextLine();
				jamEnd              = scanner.nextLong(); scanner.nextLine();
				jamSubmissionHour   = scanner.nextLong(); scanner.nextLine();
				rate                = scanner.nextLong(); scanner.nextLine();
				results             = scanner.nextLong(); scanner.nextLine();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				scanner.close();
			}
		} else {
			nextRefresh         = 0;
			start               = 0;
			compoEnd            = 0;
			compoSubmissionHour = 0;
			jamEnd              = 0;
			jamSubmissionHour   = 0;
			rate                = 0;
			results             = 0;
		}
		refresh();
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		handleMessage(event.getGuild().getId(), event.getMessage());
	}

	@Override
	public void onMessageUpdate(MessageUpdateEvent event) {
		handleMessage(event.getGuild().getId(), event.getMessage());
	}

	private void handleMessage(String id, Message message) {
		if (message.getAuthor().getId().equals("677349303672373271")) {
			return;
		}

		if (!servers.containsKey(id)) {
			servers.put(id, new ServerInfo());
		}
		var info = servers.get(id);

		var server  = message.getGuild();
		var channel = message.getChannel();

		var admin = false;
		var roles = message.getAuthor().getJDA().getRoles();
		for (var i = 0; i < roles.size() && !admin; ++i) {
			if (roles.get(i).hasPermission(Permission.ADMINISTRATOR)) {
				admin = true;
			}
		}
		if (admin) {
			if (message.getContentDisplay().equals(info.prefix + "blacklist")) {
				info.blacklist.add(channel.getId());
				channel.sendMessage("Channel has been blacklisted").queue();
			} else if (message.getContentDisplay().equals(info.prefix + "whitelist")) {
				info.blacklist.remove(channel.getId());
				channel.sendMessage("Channel has been whitelisted").queue();
			} else if (message.getContentDisplay().equals(info.prefix + "blacklist all")) {
				var channels = server.getChannels();
				for (var c : channels) {
					info.blacklist.add(c.getId());
				}
				channel.sendMessage("Blacklisted all channels").queue();
			} else if (message.getContentDisplay().equals(info.prefix + "whitelist all")) {
				info.blacklist.clear();
				channel.sendMessage("Whitelisted all channels").queue();
			}
			saveServerInfo();

			if (info.blacklist.contains(channel.getId())) {
				return;
			}

			if (message.getContentDisplay().startsWith(info.prefix + "prefix ")) {
				info.prefix = message.getContentDisplay().substring((info.prefix + "prefix ").length());
				channel.sendMessage("Updated prefix to: " + info.prefix).queue();
			}
		}

		if (info.blacklist.contains(channel.getId())) {
			return;
		}

		if (message.getContentDisplay().startsWith(info.prefix + "timeleft")) {
			refresh();
			var timeleftMessage = new StringBuilder("**Time Left**\n");
			var current = System.currentTimeMillis();
			if (current < start) {
				timeleftMessage.append("Ludum Dare starts in ").append(toDurationString(start - current));
			} else if (current < jamSubmissionHour) {
				if (current < compoEnd) {
					timeleftMessage.append("Compo ends in ").append(toDurationString(compoEnd - current)).append('\n');
				} else if (current < compoSubmissionHour) {
					timeleftMessage.append("Compo submission hour ends in ").append(toDurationString(compoSubmissionHour - current)).append('\n');
				} else {
					timeleftMessage.append("Compo rating ends in ").append(toDurationString(rate - current)).append('\n');
				}
				if (current < jamEnd) {
					timeleftMessage.append("Jam ends in ").append(toDurationString(jamEnd - current));
				} else {
					timeleftMessage.append("Jam submission hour ends in ").append(toDurationString(jamSubmissionHour - current));
				}
			} else if (current < rate) {
				timeleftMessage.append("Rating ends in ").append(toDurationString(rate - current));
			} else if (current < results) {
				timeleftMessage.append("Results are available in ").append(toDurationString(results - current));
			} else {
				timeleftMessage = new StringBuilder("Sorry I don't have any time data at the moment :(");
			}
			channel.sendMessage(timeleftMessage).queue();
		}
	}

	private void saveServerInfo() {
		try {
			ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream("ServerData.bin"));
			output.writeInt(servers.size());
			for (Map.Entry<String, ServerInfo> entry : servers.entrySet()) {
				output.writeObject(entry.getKey());
				output.writeObject(entry.getValue());
			}
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadServerInfo() {
		try {
			if (new File("ServerData.bin").exists()) {
				ObjectInputStream input = new ObjectInputStream(new FileInputStream("ServerData.bin"));
				int numObjects = input.readInt();
				servers.clear();
				for (int i = 0; i < numObjects; ++i) {
					servers.put((String) input.readObject(), (ServerInfo) input.readObject());
				}
				input.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String toDurationString(long millis) {
		var days    = (millis         ) / DAY;
		var hours   = (millis % DAY   ) / HOUR;
		var minutes = (millis % HOUR  ) / MINUTE;
		var seconds = (millis % MINUTE) / SECOND;
		return new StringBuilder()
			.append(days   ).append(" day"   ).append(days    == 1 ? ", " : "s, ")
			.append(hours  ).append(" hour"  ).append(hours   == 1 ? ", " : "s, ")
			.append(minutes).append(" minute").append(minutes == 1 ? ", " : "s, ")
			.append(seconds).append(" second").append(seconds == 1 ? ""   : "s"  )
			.toString();
	}

	private void refresh() {
		if (nextRefresh - System.currentTimeMillis() <= 0) {
			nextRefresh = System.currentTimeMillis() + REFRESH_INTERVAL;
			String[] dates = getDates();
			if (dates != null) {
				start               = getSinceEpoch(dates[0]);
				compoEnd            = getSinceEpoch(dates[1]);
				compoSubmissionHour = getSinceEpoch(dates[2]);
				jamEnd              = getSinceEpoch(dates[3]);
				jamSubmissionHour   = getSinceEpoch(dates[4]);
				rate                = getSinceEpoch(dates[5]);
				results             = getSinceEpoch(dates[6]);
				saveTimes();
			}
		}
	}

	private void saveTimes() {
		try {
			PrintWriter writer = new PrintWriter(new FileOutputStream("TimeData.txt"));
			writer.println(nextRefresh        );
			writer.println(start              );
			writer.println(compoEnd           );
			writer.println(compoSubmissionHour);
			writer.println(jamEnd             );
			writer.println(jamSubmissionHour  );
			writer.println(rate               );
			writer.println(results            );
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static long getSinceEpoch(String date) {
		var timeStrings = date.split(",");
		var times = new int[6];
		for (var i = 0; i < times.length; ++i) {
			times[i] = Integer.parseInt(timeStrings[i]);
		}
		return LocalDateTime.of(times[0], times[1] + 1, times[2] + times[3] / 24, times[3] % 24, times[4], times[5]).toEpochSecond(ZoneOffset.UTC) * 1000L;
	}

	private static String[] getDates() {
		final var JAVASCRIPT_URL_REGEX = Pattern.compile("<(/-/all\\.min\\.js\\?v=[a-zA-Z0-9\\-]+)>");
		final var DATE_UTC_REGEX = Pattern.compile("new Date\\(Date\\.UTC\\((\\d+,\\d+,\\d+,\\d+,\\d+,\\d+)\\)\\)");

		try {
			final var url = new URL("https://ldjam.com");
			var connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setInstanceFollowRedirects(true);
			connection.connect();

			var jsUrl = (URL) null;
			for (var valueLists : connection.getHeaderFields().values()) {
				for (var value : valueLists) {
					var matcher = JAVASCRIPT_URL_REGEX.matcher(value);
					if (matcher.find()) {
						var group = matcher.group(1);
						jsUrl = new URL("https://ldjam.com" + group);
					}
				}
			}
			connection.disconnect();

			if (jsUrl != null) {
				connection = (HttpURLConnection) jsUrl.openConnection();
				connection.setRequestMethod("GET");
				connection.setRequestProperty("Accept", "*/*");
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);
				connection.setInstanceFollowRedirects(true);
				connection.connect();

				var in = connection.getInputStream();
				var reader = new BufferedReader(new InputStreamReader(in));
				var builder = new StringBuilder();
				var line = (String) null;
				while ((line = reader.readLine()) != null) {
					builder.append(line).append('\n');
				}
				reader.close();
				in.close();

				var matcher = DATE_UTC_REGEX.matcher(builder);
				var dates = new String[7];
				for (var i = 0; i < dates.length; ++i) {
					if (matcher.find()) {
						dates[i] = matcher.group(1);
					} else {
						return null;
					}
				}
				return dates;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
