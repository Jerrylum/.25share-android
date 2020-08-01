package com.github.jerrylum.quartershare;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class TcpClient {
    public String serverIP = ""; //server IP address
    public int serverPort = 0;
    public Socket socket;
    // message to send to the server
    private byte[] theServerMessage;
    // sends message received notifications
    private OnMessageReceived theMessageListener = null;

    private boolean isInitialized = false;
    private boolean isRunning = false;
    private OutputStream mBufferOut;
    private InputStream mBufferIn;

    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(String ip, int port, OnMessageReceived listener) {
        theMessageListener = listener;
        serverIP = ip;
        serverPort = port;
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message byte entered by client
     */
    public void sendMessage(final byte[] message) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
            try {
                if (mBufferOut != null) {
                    Log.d("Socket", "Sending: byte array object -> " + Util.bytesToHex(message));
                    mBufferOut.write(message);
                    mBufferOut.flush();
                }
            } catch (IOException e) {
                Log.d("Socket", "Sending: byte array object failed");
            }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    /**
     * Close the connection and release the members
     */
    public void stop() {

        isInitialized = true;
        isRunning = false;

        try {
            if (mBufferOut != null) {
                mBufferOut.flush();
                mBufferOut.close();
            }
        } catch (IOException e) {

        }

        theMessageListener = null;
        mBufferIn = null;
        mBufferOut = null;
        theServerMessage = null;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void run() {

        isRunning = true;

        try {
            //here you must put your computer's IP address.
            InetAddress serverAddr = InetAddress.getByName(serverIP);

            Log.d("Socket", "C: Connecting...");

            //create a socket to make the connection with the server
            socket = new Socket(serverAddr, serverPort);

            try {

                //sends the message to the server
                mBufferOut = socket.getOutputStream();

                //receives the message which the server sends back
                mBufferIn = socket.getInputStream();


                //in this while the client listens for the messages sent by the server
                while (isRunning) {
                    Log.d("Socket", "C: Reading...");

                    byte[] buffer = new byte[2];
                    mBufferIn.read(buffer, 0, 2);
                    int how_long = (buffer[0] & 0xFF) + (buffer[1] & 0xFF) * 255;

                    if (how_long == 0) // the server closed
                        break;

                    buffer = new byte[how_long];
                    mBufferIn.read(buffer, 0, how_long);
                    theServerMessage = buffer;

                    Log.d("Socket", "C: Ok...");

                    if (theServerMessage != null && theMessageListener != null) {
                        //call the method messageReceived from MyActivity class
                        theMessageListener.messageReceived(theServerMessage);
                    }
                }
            } catch (Exception e) {
                if (theMessageListener != null)
                    theMessageListener.sendAction("toast", "Connection exception (s)");
                Log.e("Socket", "S: Error", e);
            } finally {
                //the socket must be closed. It is not possible to reconnect to this socket
                // after it is closed, which means a new socket instance has to be created.
                socket.close();
            }

        } catch (Exception e) {
            if (theMessageListener != null)
                theMessageListener.sendAction("toast", "Connection exception (c)");
            Log.e("Socket", "C: Error", e);
        }

    }

    //Declare the interface. The method messageReceived(String message) will must be implemented in the Activity
    //class at on AsyncTask doInBackground
    public interface OnMessageReceived {
        public void messageReceived(byte[] message);
        public void sendAction(String type, String message);
    }


}