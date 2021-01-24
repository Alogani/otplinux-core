package com.alogani.otpcore;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import static java.lang.Thread.*;


public class TimeProvider {
    /*
    Time values are in milliseconds
    This class provides time synced with NTP server or otherwise from system
     */

    // ----------------- VARIABLES

    static public boolean verbosity = false;
    private String[] ntpServers;
    private long clockOffset = 0;
    private int serversSynced = 0;


    // ----------------- PUBLIC CODE

    /* Better launched at start of program, cause can take time to execute
    Multithreading is used to be non blocking to other code like GUI, but can sacrifice clockOffset if its value is gotten too early */
    public void updateTime(String[] ntpServers, boolean multiThreading) { updateTime(ntpServers, multiThreading, 5, 2000, 1000); } // default 5 tries, timeout 2000ms
    public void updateTime(String[] ntpServers, boolean multiThreading, int numberOfTries, int timeOut, int retryDelay) { //timeout in milliseconds
        this.ntpServers = ntpServers;
        if ( multiThreading ) {
            Thread thread = new Thread( () -> updateTimeWithNTPServerRunnable(numberOfTries, timeOut, retryDelay) );
            thread.start();
        } else
            updateTimeWithNTPServerRunnable(numberOfTries, timeOut, retryDelay);
    }

    public Long currentTimeMillis() {
        return System.currentTimeMillis() + clockOffset;
    }

    public Long getOffsetMillus() {
        return clockOffset;
    }

    public int numberOfServersReached() {
        return serversSynced;
    }

    // ----------------- PRIVATE CODE
    // actual execution code
    private void updateTimeWithNTPServerRunnable(int numberOfTries, int timeOut, int retryDelay) {
        int numberReached = 0;
        for (String defaultNTPServer : ntpServers) {
            for (int trynum = 0; trynum < numberOfTries; trynum++) {
                if (verbosity) System.out.print("Try " + (trynum + 1) + " on ");
                try {
                    clockOffset += connectAndGetOffset(defaultNTPServer, timeOut);
                } catch (Exception e) {
                    try {
                        sleep(retryDelay);
                    } catch (InterruptedException ignored) { } //do not brute retry immediately
                    continue;
                } //retry until connected
                numberReached++;
                break;
            }
        }
        clockOffset /= numberReached; // do the average of the sum of the offsets
        serversSynced = numberReached;
    }

    // actual connection with a server
    private long connectAndGetOffset(String server, int timeOut) throws Exception {
        NTPUDPClient client = new NTPUDPClient();
        client.setDefaultTimeout(timeOut);
        InetAddress hostAddr;
        TimeInfo info;

        try {
            client.open();
            hostAddr = InetAddress.getByName(server);
            info = client.getTime(hostAddr);
        } catch (SocketTimeoutException e) {
            if ( TimeProvider.verbosity ) System.out.println("server \"" + server + "\" : connection timeout");
            throw e;
        } catch (IOException e) {
            if ( TimeProvider.verbosity ) System.out.println("server \"" + server + "\" : connection failed");
            throw e;
        }

        client.close();
        info.computeDetails();
        if ( TimeProvider.verbosity ) {
            System.out.println("server \"" + server + "\" : connection succeed");
            System.out.println("Roundtrip delay(ms) : " + info.getDelay() + ", clock offset (ms): " + info.getOffset());
        }
        return info.getOffset();
    }

}
