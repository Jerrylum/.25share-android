package com.github.jerrylum.quartershare;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class MainActivity extends AppCompatActivity {

    EditText etIP, etPort, etMessage;
    CheckBox cbTrim;

    ClipboardManager clipboard;

    ConnectTask usingTask = null;

    String lastMsgUUID = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);
        etMessage = findViewById(R.id.etMessage);
        cbTrim = findViewById(R.id.cbTrim);

        SharedPreferences config = getConfig();
        etIP.setText(config.getString("ip", "192.168.0.2"));
        etPort.setText(config.getString("port", "7984"));
        etMessage.setTextIsSelectable(true);
        etMessage.setFocusableInTouchMode(true);

        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public void saveBtn_onClick(View view) {
        setConfig().putString("ip", etIP.getText().toString())
                   .putString("port", etPort.getText().toString())
                   .commit();
    }

    public void connectBtn_onClick(View view) {
        if (usingTask != null) {
            usingTask.stopClient();
            usingTask = null;
        }
        usingTask = new ConnectTask();
        usingTask.execute();
    }

    public void insertBtn_OnClick(View view) {
        etMessage.getText().insert(etMessage.getSelectionStart(), ((Button)view).getText());
    }

    public void goBtn_OnClick(View view) {
        if (usingTask != null) {
            String msg = etMessage.getText().toString();

            if (cbTrim.isChecked())
                msg = trimMessage(msg);

            lastMsgUUID = UUID.randomUUID().toString().replace("-", "");

            String send = lastMsgUUID + ">" + msg;

            usingTask.sendMessage(send);
        }
    }

    public class ConnectTask extends AsyncTask<String, byte[], TcpClient> {

        private Cipher serverEncryptCipher = null;
        private Cipher shareAESEncryptCipher = null;
        private Cipher shareAESDecryptCipher = null;

        private TcpClient usingTcpClient;

        @Override
        protected TcpClient doInBackground(String... message) {
            String ip = etIP.getText().toString();
            int port = Integer.parseInt(etPort.getText().toString());

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
                showToast(msg);
            }
        }

        public void sendMessage(String msg) {
            Log.d("Socket", "prepare to send string message: " + msg);
            sendMessage(msg.getBytes());
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
            usingTcpClient.stopClient();
        }


        private RSAPublicKey covertToPublicKey(byte[] server_rsa_public_key_content_bytes){

            try {
                X509EncodedKeySpec spec =
                        new X509EncodedKeySpec(server_rsa_public_key_content_bytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                Log.d("Socket", "Created Encoded Key Spec");
                return (RSAPublicKey)kf.generatePublic(spec);
            } catch (Exception e) {
                Log.d("Socket", "Failed to created generate public key");
                Log.d("Socket", e.toString());
                return null;
            }
        }

        private void handleServerPublicKeyAndGenAESKey(String publicKey) {
            try {
                byte[] server_rsa_public_key_content_bytes = Base64.getDecoder().decode(publicKey);
                RSAPublicKey serverPublicKey = covertToPublicKey(server_rsa_public_key_content_bytes);

                serverEncryptCipher = Cipher.getInstance("RSA/NONE/PKCS1Padding"); //NoSuchPaddingException
                serverEncryptCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey); //InvalidKeyException

                Log.d("Socket", "Server Public Key: ok");
            } catch (Exception e) {
                Log.d("Socket", "Decode server public key failed");
                Log.d("Socket", publicKey);
            }


            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                SecretKey key = generator.generateKey();

                // Important, send the key first, then set the variable "shareAESCipher"

                this.sendMessage("aeskey>" + Base64.getEncoder().encodeToString(key.getEncoded()));

                IvParameterSpec iv = new IvParameterSpec("ABCDEFGHIJKLMNOP".getBytes());


                shareAESEncryptCipher = Cipher.getInstance("AES/CFB/NoPadding");
                shareAESEncryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);
                shareAESDecryptCipher = Cipher.getInstance("AES/CFB/NoPadding");
                shareAESDecryptCipher.init(Cipher.DECRYPT_MODE, key, iv);

                Log.d("Socket", "Share AES Key: " + TcpClient.bytesToHex(key.getEncoded()));
            } catch (Exception e) {
                Log.d("Socket", "Create share aes key failed");
                Log.d("Socket", e.toString());
            }
        }


        private void connectTask_OnResponse(byte[] raw) {
            if (this.serverEncryptCipher == null || this.shareAESEncryptCipher == null) {
                String response = new String(raw);

                String[] split = response.split("<");
                String type = split[0]; // TODO
                String msg = split[1];

                handleServerPublicKeyAndGenAESKey(msg);
            } else {
                try {
                    String response = new String(shareAESDecryptCipher.doFinal(raw));

                    String[] split = response.split("<");
                    String type = split[0];
                    String msg = split[1];

                    if (type.equals("pong")) {
                        if (msg.equals(lastMsgUUID)) { // the server echo back the message uuid, clear the input
                            etMessage.setText("");
                        }
                    } else if (type.equals("copy")) {
                        ClipData clip = ClipData.newPlainText("Copied Text", msg);
                        clipboard.setPrimaryClip(clip);

                        showToast("Copied");
                    }
                } catch (Exception e) {
                    Log.d("Socket", "Decrypt share aes message failed");
                    Log.d("Socket", e.toString());
                }
            }


        }

    }



    public SharedPreferences.Editor setConfig() {
        return getPreferences(0).edit();
    }

    public SharedPreferences getConfig() {
        return getPreferences(0);
    }

    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private static String trimMessage(String raw){
        String rtn = "";

        char lastChar = ' ';
        for (int i = 0; i < raw.length(); i++) {
            char nowChar = raw.charAt(i);

            switch (lastChar) { // Important: be careful
                case '，':
                case '。':
                case '、':
                case '：':
                case '；':
                case '？':
                case '！':
                    if (nowChar == ' ')
                        break;
                default:
                    rtn += nowChar;
                    break;
            }

            lastChar = nowChar;
        }

        return rtn;
    }


}