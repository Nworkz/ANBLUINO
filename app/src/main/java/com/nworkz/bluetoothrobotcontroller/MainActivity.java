package com.nworkz.bluetoothrobotcontroller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.icu.util.Output;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener{


    final int MEASURE_DISTANCE = 3;
    final int REQUEST_ENABLE_BT = 1;
    final int MY_PERMISSIONS_REQUEST_COARSE_LOCATION = 2;

    //private BluetoothSocket mSocket;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    List<BluetoothDevice> pairedDevicesList;
    BluetoothDevice connectWith;

    //UI
    ImageView up, down, left, right, a ,b;
    TextView  measurement, connectionStatus;
    Switch startConnection;
    Spinner BluetoothDevicesSpinner;

    //
    ConnectThread connectBluetooth;
    TransactionThread startTransaction;

    //
    double totalDistance = 0.0f;
    int revCount = 0;
    //boolean isForward = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pairedDevicesList = new ArrayList<>();


        prepareUI();

        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {

                byte[] msgB = (byte[]) msg.obj;
                switch (msg.what){
                    case MEASURE_DISTANCE:
                        String revString = new String(msgB);
                        if(revString.charAt(0) == 'r'){
                            revCount++;
                            totalDistance = (0.055 * Math.PI) * (revCount/143);
                        }else if(revString.charAt(0) == 't'){
                            revCount--;
                            totalDistance = (0.055 * Math.PI) * (revCount/143);
                        }

                        measurement.setText(String.format("%.3f",totalDistance));
                        //measurement.setText(revCount + "");

                        break;
                }
            }
        };

        requestLocationPermission();
        UIInteraction();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    enableBluetooth();

                }
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }

    }

    @Override
    protected void onDestroy() {
        if(connectBluetooth.isAlive()) {
            connectBluetooth.cancel();
            connectBluetooth = null;
        }
        super.onDestroy();
    }

    private void prepareUI(){
        up = findViewById(R.id.up_button);
        down = findViewById(R.id.down_button);
        left = findViewById(R.id.left_button);
        right = findViewById(R.id.right_button);

        a = findViewById(R.id.a_button);
        b = findViewById(R.id.b_button);

        connectionStatus = findViewById(R.id.connection_status);
        startConnection = findViewById(R.id.switch1);
        BluetoothDevicesSpinner = findViewById(R.id.spinner);
        measurement = findViewById(R.id.measurement_value);


    }

    @SuppressLint("ClickableViewAccessibility")
    private void UIInteraction(){
        getPairedBluetoothDevices();

        BluetoothDevicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                connectWith = pairedDevicesList.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        startConnection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    connectBluetooth = new ConnectThread(connectWith);
                    connectBluetooth.start();
                }else{
                    connectBluetooth.cancel();
                }
            }
        });


        up.performClick();
        down.performClick();
        left.performClick();
        right.performClick();
        a.performClick();
        b.performClick();

        up.setOnTouchListener(this);
        down.setOnTouchListener(this);
        left.setOnTouchListener(this);
        right.setOnTouchListener(this);
        a.setOnTouchListener(this);
        b.setOnTouchListener(this);


    }

    //coarse location permission
    private void requestLocationPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_COARSE_LOCATION);

            }
        } else {
            enableBluetooth();
        }

    }

    //bluetooth
    private void enableBluetooth(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "This device does not support bluetooth", Toast.LENGTH_SHORT).show();
        }else{
            if(!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

        }

    }

    private void getPairedBluetoothDevices(){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        List<String> pairedDevicesName = new ArrayList<>();
        for(BluetoothDevice device : pairedDevices){
            pairedDevicesList.add(device);
            pairedDevicesName.add(device.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, pairedDevicesName);
        BluetoothDevicesSpinner.setAdapter(adapter);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN){
            switch (v.getId()){
                case R.id.up_button :
                    //isForward = true;
                    try{
                        connectBluetooth.mmtransactThread.write("u");
                    }catch (Exception e){
                        Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
                    }

                    break;
                case R.id.down_button :
                    //isForward = false;
                    try{
                        connectBluetooth.mmtransactThread.write("d");
                    }catch (Exception e){
                        Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.left_button :
                    try{
                        connectBluetooth.mmtransactThread.write("l");
                    }catch (Exception e){
                        Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.right_button :
                    try{
                        connectBluetooth.mmtransactThread.write("r");
                    }catch (Exception e){
                        Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.a_button:
                    try{
                        connectBluetooth.mmtransactThread.write("a");
                    }catch(Exception e){}
                    break;
                case R.id.b_button:
                    revCount = 0;
                    totalDistance = 0;
                    measurement.setText("0.000");
                    break;
            }
        }
        else if(event.getAction() == MotionEvent.ACTION_UP){
            try{
                connectBluetooth.mmtransactThread.write("x");
            }catch (Exception e){}
        }
        return true;
    }

    //connect
    private class ConnectThread extends Thread{

        private final BluetoothSocket mmSocket;
        //private final BluetoothDevice mmDevice;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        public TransactionThread mmtransactThread;


        public ConnectThread(BluetoothDevice device){
            BluetoothSocket tmpSocket = null;
            //mmDevice = device;
            try{
                tmpSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e){}

            mmSocket = tmpSocket;
        }

        public void run(){
            mBluetoothAdapter.cancelDiscovery();
            try{
                mmSocket.connect();
                if(mmSocket.isConnected()) {
                    connectionStatus.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus.setText("Status: Connected");
                            connectionStatus.setTextColor(Color.GREEN);
                            }
                    });
                }else{
                    startConnection.post(new Runnable() {
                        @Override
                        public void run() {
                            startConnection.setChecked(false);
                        }
                    });

                    connectionStatus.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus.setText("Status: Connected");
                            connectionStatus.setTextColor(Color.GREEN);
                        }
                    });
                }
            }catch (IOException connectExeption){
                try{
                    mmSocket.close();
                }catch (IOException closeException){}
            }
            mmtransactThread = new TransactionThread(mmSocket);
            mmtransactThread.start();
        }

        public void cancel(){
            try {
                mmSocket.close();
                mmtransactThread.cancel();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //transaction
    private class TransactionThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public TransactionThread(BluetoothSocket socket){
            mmSocket = socket;
            InputStream tmpInStream = null;
            OutputStream tmpOutStream = null;

            try{
                tmpInStream = socket.getInputStream();
                tmpOutStream = socket.getOutputStream();
            }catch (IOException e){
                Log.e("transact", e.getMessage());
            }

            mmInStream = tmpInStream;
            mmOutStream = tmpOutStream;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;

            while(true){
                try{
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for(int i = begin; i <  bytes; i++){
                        mHandler.obtainMessage(MEASURE_DISTANCE, begin, i, buffer).sendToTarget();
                        begin = i + 1;
                        if(i == bytes - 1){
                            bytes = 0;
                            begin = 0;
                        }
                    }

                }catch (Exception e){
                    Log.e("transact",e.getMessage());
                }
            }
        }

        public void write(String code){
            byte[] b = code.getBytes();
            try {
                mmOutStream.write(b);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

