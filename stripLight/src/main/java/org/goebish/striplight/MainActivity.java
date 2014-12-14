package org.goebish.striplight;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.ColorPicker.OnColorChangedListener;
import com.larswerkman.holocolorpicker.SVBar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements OnColorChangedListener {

    Button buttonPower;
    Button buttonMode;
    Button buttonLumMinus;
    Button buttonLumPlus;
    Button buttonSpeedMinus;
    Button buttonSpeedPlus;
    ColorPicker picker;
    SVBar svBar;
    TextView textViewState;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    boolean init;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        picker = (ColorPicker) findViewById(R.id.picker);
        svBar = (SVBar) findViewById(R.id.svbar);
        picker.addSVBar(svBar);
        picker.setOnColorChangedListener(this);
        textViewState = (TextView) findViewById(R.id.textViewState);

        buttonPower = (Button) findViewById(R.id.buttonPower);
        buttonPower.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('P');            // Power
            }
        });

        buttonMode = (Button) findViewById(R.id.buttonMode);
        buttonMode.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('M');            // Mode
            }
        });
        buttonMode.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('R');            // Random
                return true;
            }
        });

        buttonLumMinus = (Button) findViewById(R.id.buttonDarker);
        buttonLumMinus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('D');            // Darker
            }
        });

        buttonLumPlus = (Button) findViewById(R.id.buttonBrighter);
        buttonLumPlus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('B');            // Brighter
            }
        });

        buttonSpeedMinus = (Button) findViewById(R.id.buttonSlower);
        buttonSpeedMinus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('S');            // Slower
            }
        });

        buttonSpeedPlus = (Button) findViewById(R.id.buttonFaster);
        buttonSpeedPlus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSerialData(0x55);
                sendSerialData(0xAA);
                sendSerialData('F');            // Faster
            }
        });

        onResume();
    }

    @Override
    protected void onResume() {
        try {
            if (findBT())
                openBT();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        try {
            if (textViewState.getText() == "Connected")
                closeBT();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        super.onPause();
    }

    @Override
    public void onColorChanged(int color) {
        //gives the color when it's changed.
        color = svBar.getColor();
        picker.setOldCenterColor(color);
        if (init) {
            return;
        }
        //spinnerMode.setSelection(0);
        sendSerialData(0x55);
        sendSerialData(0xAA);
        sendSerialData('C');        // Manual color
        sendSerialData(color >> 16);    // R
        sendSerialData(color >> 8);    // G
        sendSerialData(color);        // B
    }

    void sendSerialData(int data) {
        if (mmOutputStream != null)
            try {
                mmOutputStream.write(data);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
    }

    boolean findBT() {
        if (mmDevice != null) // already found
            return true;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            textViewState.setText("No bluetooth adapter available");
            return false;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
                .getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("StripLight")) // bluetooth device name
                {
                    mmDevice = device;
                    Log.v("StripLight",
                            "findBT found device named " + mmDevice.getName());
                    Log.v("StripLight",
                            "device address is " + mmDevice.getAddress());
                    break;
                }
            }
        }
        textViewState.setText(mmDevice == null ? "Bluetooth device not found" : "Not Connected");
        return mmDevice != null;
    }

    void openBT() throws IOException {

        if (mmDevice != null) {
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // Standard SerialPortService ID
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();
            textViewState.setText("Connected");
            beginListenForData();
            sendSerialData(0x55);
            sendSerialData(0xAA);
            sendSerialData('G'); // ask current mode and color
        }
    }

    void closeBT() throws IOException {
        stopWorker = true;
        if (mmSocket != null) {
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
        }
        textViewState.setText("Bluetooth Closed");
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte end1 = (byte) 0x55;
        final byte end2 = (byte) 0xAA;
        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (readBufferPosition > 0 && b == end2 && readBuffer[readBufferPosition - 1] == end1) {
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        public void run() {
                                            init = true;
                                            int color = ((int) readBuffer[1] << 16) | ((int) readBuffer[2] << 8) | (int) readBuffer[3];
                                            picker.setColor(color);
                                            init = false;
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
    }

}