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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

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

    public class ConnectTask extends AsyncTask<String, String, TcpClient> {

        private Cipher serverEncryptCipher = null;
        private Cipher shareAESCipher = null;

        private TcpClient usingTcpClient;

        @Override
        protected TcpClient doInBackground(String... message) {
            String ip = etIP.getText().toString();
            int port = Integer.parseInt(etPort.getText().toString());

            //we create a TCPClient object
            usingTcpClient = new TcpClient(ip, port, new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String type, String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(type, message);
                }
            });
            usingTcpClient.run();

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);



            String type = values[0];
            String msg = values[1];

            if (type.equals("from")) {             //response received from server
                Log.d("Socket", "Response: " + msg);

                connectTask_OnResponse(msg);       //response received from the tcp client
            } else if (type.equals("toast")) {
                showToast(msg);
            }
        }

        public void sendMessage(String msg) {
            sendMessage(msg.getBytes());
        }

        public void sendMessage(byte[] msg) {
            try {
                if (serverEncryptCipher != null) {
                    usingTcpClient.sendMessage(serverEncryptCipher.doFinal(msg));
                } else {
                    Log.d("Socket", "Server cipher not found");
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


        private void connectTask_OnResponse(String response) {
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
            } else if (type.equals("rsa")) {
                // TODO check is key always exists


                try {
                    byte[] server_rsa_public_key_content_bytes = Base64.getDecoder().decode(msg);
                    RSAPublicKey serverPublicKey = covertToPublicKey(server_rsa_public_key_content_bytes);

                    serverEncryptCipher = Cipher.getInstance("RSA/NONE/PKCS1Padding"); //NoSuchPaddingException
                    serverEncryptCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey); //InvalidKeyException

                    Log.d("Socket", "Server Public Key: ok");
                } catch (Exception e) {
                    Log.d("Socket", "Decode server public key failed");
                    Log.d("Socket", e.toString());
                }


                try {
//                    KeyPairGenerator keyPairGenerator = null;
//                    keyPairGenerator = KeyPairGenerator.getInstance("RSA"); //NoSuchAlgorithmException
//
//                    keyPairGenerator.initialize(1024);
//                    KeyPair keyPair = keyPairGenerator.generateKeyPair();
//                    RSAPublicKey rsaPublicKey = (RSAPublicKey)keyPair.getPublic();
//                    RSAPrivateKey rsaPrivateKey = (RSAPrivateKey)keyPair.getPrivate();
//
//                    myselfDecryptCipher = Cipher.getInstance("RSA/NONE/PKCS1Padding"); //NoSuchPaddingException
//                    myselfDecryptCipher.init(Cipher.DECRYPT_MODE,rsaPrivateKey); //InvalidKeyException

                    KeyGenerator generator = KeyGenerator.getInstance("AES");
                    generator.init(256);
                    SecretKey key = generator.generateKey();

                    shareAESCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    shareAESCipher.init(Cipher.ENCRYPT_MODE, key);


//                    byte[] a = "aeskey>".getBytes();
//                    byte[] b = key.getEncoded();
//                    byte[] c = new byte[a.length + b.length];
//                    System.arraycopy(a, 0, c, 0, a.length);
//                    System.arraycopy(b, 0, c, a.length, b.length);

                    this.sendMessage("aeskey>" + Base64.getEncoder().encodeToString(key.getEncoded()));

                    //Log.d("Socket", "Myself Public Key: " + Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded()));
                    //Log.d("Socket", "Myself Private Key: " + new String(rsaPrivateKey.getEncoded()));
                } catch (Exception e) {
                    Log.d("Socket", "Create share aes key failed");
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