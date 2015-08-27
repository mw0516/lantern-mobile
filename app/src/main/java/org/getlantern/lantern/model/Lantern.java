package org.getlantern.lantern.model;

import android.util.Log;

import java.net.InetAddress;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import go.client.*;
import org.getlantern.lantern.service.LanternVpn;
import org.getlantern.lantern.config.LanternConfig;

public class Lantern extends Client.SocketProvider.Stub {

    private static final String TAG = "Lantern";
    private LanternVpn service;
    private FileOutputStream outputStream;
    private Client.GoCallback.Stub callback;

    //
    public Lantern(LanternVpn service) {
        this.service = service;
        this.setupCallbacks();
    }

    // Configures callbacks from Lantern during packet
    // processing
    private void setupCallbacks() {
        final Lantern service = this;
        this.callback = new Client.GoCallback.Stub() {
            public void AfterStart() {
                Log.d(TAG, "Lantern successfully started.");
            }

            public void AfterConfigure() {
                Log.d(TAG, "Lantern successfully configured.");
            }

            public void WritePacket(byte[] bytes) {
                service.returnPacket(bytes);
                // Just used to demonstrate a callback after intercepting a packet
            }

        };
    }

    public void start(final InetAddress localIP, final int port) {
        try {
            Log.d(TAG, "About to start Lantern..");
            String lanternAddress = String.format("%s:%d",
                    localIP.getHostAddress(), port);
            Client.RunClientProxy(lanternAddress,
                    LanternConfig.APP_NAME, this, callback);
            // Wait a few seconds for processing until Lantern starts
            Thread.sleep(3000);
            // Configure Lantern and interception rules
            Client.Configure(this, callback);

        } catch (final Exception e) {
            Log.e(TAG, "Fatal error while trying to run Lantern: " + e);
            throw new RuntimeException(e);
        }
    }

    // Protect is used to exclude a socket specified by fileDescriptor
    // from the VPN connection. Once protected, the underlying connection
    // is bound to the VPN device and won't be forwarded
    @Override
    public void Protect(long fileDescriptor) throws Exception {
        if (!this.service.protect((int) fileDescriptor)) {
            throw new Exception("protect socket failed");
        }
    }


    // Runs a simple HTTP GET to verify Lantern is able to open connections
    public void testConnect() {
        try {
            final String testAddr = "www.example.com:80";
            Client.TestConnect(this, testAddr);
        } catch (final Exception e) {

        }
    }

    public void configure(FileOutputStream out) {
        this.outputStream = out;

    }

    public void returnPacket(byte[] bytes) {
        try {
            Log.d(TAG, "Received a response packet: " + bytes.length);
            outputStream.write(bytes);
            outputStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error writing to output stream: " + e);
        }
        // Just used to demonstrate a callback after intercepting a packet
    }

    // As packets arrive on the VpnService, processPacket sends the raw bytes
    // to Lantern for processing
    public void processPacket(final ByteBuffer packet) {
        try {
            Client.ProcessPacket(packet.array(), this, callback);
        } catch (final Exception e) {
            Log.e(TAG, "Unable to process incoming packet!");
        }
    }
}
