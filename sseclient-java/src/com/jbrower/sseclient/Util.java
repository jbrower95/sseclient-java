package com.jbrower.sseclient;

public final class Util {
	/* Attempts to sleep for ms number of milliseconds.*/
	public static void sleep(double ms) {
		try {
			Thread.sleep((int)ms);
		} catch (InterruptedException e) {
			/* Eat the error.*/
			return;
		}
	}
}
