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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class MainActivity extends AppCompatActivity {

    EditText etIP, etPort, etMessage;
    CheckBox cbTrim;
    TextView tvSecurityCode;
    LinearLayout llSecurityPanel;

    ClipboardManager clipboard;
    Random rand = null;

    ConnectTask usingTask = null;

    byte[] lastMsgId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);
        etMessage = findViewById(R.id.etMessage);
        cbTrim = findViewById(R.id.cbTrim);
        tvSecurityCode = findViewById(R.id.tvSecurityCode);
        llSecurityPanel = findViewById(R.id.llSecurityPanel);

        SharedPreferences config = getConfig();
        etIP.setText(config.getString("ip", "192.168.0.2"));
        etPort.setText(config.getString("port", "7984"));
        etMessage.setTextIsSelectable(true);
        etMessage.setFocusableInTouchMode(true);

        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        rand = new Random();
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
        usingTask.execute(this);

        getSupportActionBar().setSubtitle("Connecting");
    }

    public void insertBtn_OnClick(View view) {
        etMessage.getText().insert(etMessage.getSelectionStart(), ((Button)view).getText());
    }

    public void goBtn_OnClick(View view) {
        if (usingTask != null) {
            String msg = etMessage.getText().toString();

            if (cbTrim.isChecked())
                msg = Util.trimMessage(msg);

            lastMsgId = new byte[4];
            rand.nextBytes(lastMsgId);

            usingTask.sendMessage(Util.joinByteArray(lastMsgId, msg.getBytes()));
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


}