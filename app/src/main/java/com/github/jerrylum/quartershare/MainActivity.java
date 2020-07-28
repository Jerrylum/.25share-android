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

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    EditText etIP, etPort, etMessage;
    CheckBox cbTrim;

    ClipboardManager clipboard;

    TcpClient usingTcpClient;
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
        if (usingTcpClient != null) {
            usingTcpClient.stopClient();
            usingTcpClient = null;
        }
        usingTask = new ConnectTask();
        usingTask.execute();
    }

    public void insertBtn_OnClick(View view) {
        etMessage.getText().insert(etMessage.getSelectionStart(), ((Button)view).getText());
    }

    public void goBtn_OnClick(View view) {
        if (usingTcpClient != null) {
            String msg = etMessage.getText().toString();

            if (cbTrim.isChecked())
                msg = trimMessage(msg);

            lastMsgUUID = UUID.randomUUID().toString().replace("-", "");

            String send = lastMsgUUID + ">" + msg;

            usingTcpClient.sendMessage(send);
        }
    }

    public void connectTask_OnResponse(String response) {
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
    }

    public class ConnectTask extends AsyncTask<String, String, TcpClient> {

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


            //response received from server
            String type = values[0];
            String msg = values[1];

            if (type.equals("from")) {
                Log.d("Socket", "Response: " + msg);

                connectTask_OnResponse(msg);
            } else if (type.equals("toast")) {
                showToast(msg);
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