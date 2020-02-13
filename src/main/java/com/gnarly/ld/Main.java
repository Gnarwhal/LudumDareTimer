package com.gnarly.ld;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;

import javax.security.auth.login.LoginException;

public class Main {

	public static void main(String[] args) throws LoginException {
		LDTimerEventListener events = new LDTimerEventListener();
		JDA jda = new JDABuilder(AccountType.BOT).setToken(args[0]).build();
		jda.addEventListener(events);
	}
}
