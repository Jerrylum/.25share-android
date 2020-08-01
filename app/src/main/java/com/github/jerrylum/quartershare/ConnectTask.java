package com.github.jerrylum.quartershare;


import android.content.ClipData;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class ConnectTask extends AsyncTask<MainActivity, byte[], TcpClient> {

    private Cipher serverEncryptCipher = null;
    private Cipher shareAESEncryptCipher = null;
    private Cipher shareAESDecryptCipher = null;

    private TcpClient usingTcpClient;
    private MainActivity activity;

    @Override
    protected TcpClient doInBackground(MainActivity... message) {
        activity = message[0];

        String ip = activity.etIP.getText().toString();
        int port = Integer.parseInt(activity.etPort.getText().toString());



        //we create a TCPClient object
        usingTcpClient = new TcpClient(ip, port, new TcpClient.OnMessageReceived() {
            @Override
            public void messageReceived(byte[] message) {
                //this method calls the onProgressUpdate
                publishProgress("from".getBytes(), message);
            }

            @Override
            public void sendAction(String type, String message) {
                //this method calls the onProgressUpdate
                publishProgress(type.getBytes(), message.getBytes());
            }
        });

        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (usingTcpClient.isInitialized()) {
                    if (usingTcpClient.isRunning() == false) {
                        this.cancel();
                        return;
                    }
                }
                if (usingTcpClient.socket != null) {
                    String msg = usingTcpClient.socket.isClosed() ? "Disconnected" : "Connected";
                    publishProgress("subtitle".getBytes(), msg.getBytes());
                }
            }
        },300,500);

        usingTcpClient.run();


        return null;
    }

    @Override
    protected void onProgressUpdate(byte[]... values) {
        super.onProgressUpdate(values);

        String type = new String(values[0]);

        if (type.equals("from")) {             //response received from server
            Log.d("Socket", "Response");

            connectTask_OnResponse(values[1]);       //response received from the tcp client
        } else if (type.equals("toast")) {
            String msg = new String(values[1]);
            activity.showToast(msg);
        } else if (type.equals("subtitle")) {
            String msg = new String(values[1]);
            activity.getSupportActionBar().setSubtitle(msg);
        }

    }

    public void sendMessage(byte[] msg) {
        try {
            if (shareAESEncryptCipher != null) {
                usingTcpClient.sendMessage(shareAESEncryptCipher.doFinal(msg));
                Log.d("Socket", "send message using share aes cipher");
            } else if (serverEncryptCipher != null) {
                usingTcpClient.sendMessage(serverEncryptCipher.doFinal(msg));
                Log.d("Socket", "send message using server cipher");
            } else {
                Log.d("Socket", "no cipher found");
            }
        } catch (Exception ex) {
            Log.d("Socket", "Encryption using server cipher failed");
            Log.d("Socket", ex.toString());
        }

    }

    public void stopClient() {
        usingTcpClient.stop();
    }


    private RSAPublicKey covertToPublicKey(byte[] server_rsa_public_key_content_bytes){

        try {
            X509EncodedKeySpec spec =
                    new X509EncodedKeySpec(server_rsa_public_key_content_bytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            Log.d("Socket", "Created Encoded Key Spec");
            return (RSAPublicKey)kf.generatePublic(spec);
        } catch (Exception e) {
            Log.d("Socket", "Failed to create Encoded Key Spec");
            Log.d("Socket", e.toString());
            return null;
        }
    }

    private void handleServerPublicKeyAndGenAESKey(String publicKey) {
        byte[] server_rsa_public_key_content_bytes;
        SecretKey key;

        try {
            server_rsa_public_key_content_bytes = Base64.getDecoder().decode(publicKey);
            RSAPublicKey serverPublicKey = covertToPublicKey(server_rsa_public_key_content_bytes);

            serverEncryptCipher = Cipher.getInstance("RSA/NONE/PKCS1Padding"); //NoSuchPaddingException
            serverEncryptCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey); //InvalidKeyException

            activity.getSupportActionBar().setSubtitle("Received RSA Key from server");
        } catch (Exception e) {
            activity.getSupportActionBar().setSubtitle("Failed to decode RSA Key from server");
            Log.d("Socket", publicKey);

            return;
        }

        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
            byte[] iv_byte = new byte[16];
            SecureRandom.getInstanceStrong().nextBytes(iv_byte);
            IvParameterSpec iv = new IvParameterSpec(iv_byte);

            // Important, send the key first, then set the variable "shareAESCipher"

            this.sendMessage(Util.joinByteArray(key.getEncoded(), iv_byte));

            shareAESEncryptCipher = Cipher.getInstance("AES/CFB/NoPadding");
            shareAESEncryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);
            shareAESDecryptCipher = Cipher.getInstance("AES/CFB/NoPadding");
            shareAESDecryptCipher.init(Cipher.DECRYPT_MODE, key, iv);

            activity.getSupportActionBar().setSubtitle("Generated AES encryption key");
        } catch (Exception e) {
            activity.getSupportActionBar().setSubtitle("Failed to generate AES encryption key");
            Log.d("Socket", e.toString());
            return;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(server_rsa_public_key_content_bytes);
            md.update(key.getEncoded());
            String security_code = Util.bytesToHex(md.digest());

            String display = "";

            for (int i = 0; i < security_code.length(); i++) {
                if (i != 0) {
                    if (i % 16 == 0)
                        display += '\n';
                    else if (i % 4 == 0)
                        display += ' ';
                }

                display += security_code.charAt(i);

            }

            activity.llSecurityPanel.setVisibility(View.VISIBLE);
            activity.tvSecurityCode.setText(display);

            activity.getSupportActionBar().setSubtitle("Sent the key to the server");
        } catch (Exception e) {
            Log.d("Socket", "Create security code failed");
            Log.d("Socket", e.toString());
        }
    }


    private void connectTask_OnResponse(byte[] raw) {
        if (this.serverEncryptCipher == null || this.shareAESEncryptCipher == null) {
            String response = new String(raw);

            handleServerPublicKeyAndGenAESKey(response);
        } else {
            try {
                byte[] result = shareAESDecryptCipher.doFinal(raw);
                ByteArrayInputStream stream = new ByteArrayInputStream(result);

                int type_flag = (result[0] & 0xFF);

                byte[] content_byte = new byte[result.length - 1];
                stream.skip(1);
                stream.read(content_byte);

                if (type_flag == ServerPackageFlag.PONG_FLAG) {
                    if (Arrays.equals(content_byte, activity.lastMsgId)) { // the server echo back the message uuid, clear the input
                        activity.etMessage.setText("");
                    }
                } else if (type_flag == ServerPackageFlag.COPY_FLAG) {
                    String msg = new String(content_byte);
                    ClipData clip = ClipData.newPlainText("Copied Text", msg);
                    activity.clipboard.setPrimaryClip(clip);

                    activity.showToast("Copied");
                }
            } catch (Exception e) {
                Log.d("Socket", "Decrypt share aes message failed");
                Log.d("Socket", e.toString());
            }
        }


    }

}
