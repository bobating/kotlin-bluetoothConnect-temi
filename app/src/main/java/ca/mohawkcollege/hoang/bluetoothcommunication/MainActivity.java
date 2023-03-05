package ca.mohawkcollege.hoang.bluetoothcommunication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button listen,send, listDevices;
    ListView listView;
    TextView msg_box,status;
    EditText writeMsg;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;

    SendRecieve sendRecieve;


    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED= 5;

    int REQUEST_ENABLE_BLUETOOTH=1;

    private static final String APP_NAME = "BT CHAT";
    private static final UUID MY_UUID =UUID.fromString("e6ae54d5-2a9c-431f-ae38-93c3096a48b2");
    public Set<BluetoothDevice> bt;


    /**
     * check if bluetooth is enabled then turns on if needed
     * calls implement listeners() method lot listen for the onClick events
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById();
        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();


        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
        }

        implementListeners();
    }

    /**
     * get past bonded device and then display on the listview
     */
    private void implementListeners() {
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                status.setText("1listing");
                try{
                    bt=bluetoothAdapter.getBondedDevices();
                    String[] strings=new String[bt.size()];
                    btArray=new BluetoothDevice[bt.size()];
                    int index=0;
                        if( bt.size()>0)
                        {
                            for(BluetoothDevice device : bt)
                            {
                                btArray[index]= device;
                                strings[index]=device.getName();
                                index++;
                            }
                            ArrayAdapter<String> arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,strings);
                            listView.setAdapter(arrayAdapter);
                            status.setText("2listing");
                        }
                }catch (Exception e){
                    status.setText((CharSequence) e);
                }


            }
        });

        /**
         * calls server class to await connection
         */
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ServerClass serverClass=new ServerClass();
                serverClass.start();
            }
        });
        /**
         * when item pressed on listview calls clientclass to start connection
         */
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ClientClass clientClass=new ClientClass(btArray[i]);
                clientClass.start();


                status.setText("Connecting");
            }
        });

        /**
         * retrieves string from edit text, then calls sendReceieve.write with string
         */
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String string= String.valueOf(writeMsg.getText());
                sendRecieve.write(string.getBytes());
            }
        });
    }

    Handler handler=new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what)
            {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED: // receives message and displays in message box
                    byte[] readBuff= (byte[]) msg.obj;
                    String tempMsg=new String(readBuff,0,msg.arg1);
                    msg_box.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    private void findViewById() {
        listen= (Button) findViewById(R.id.listen);
        send= (Button) findViewById(R.id.send);
        listView= (ListView) findViewById(R.id.listview);
        msg_box= (TextView) findViewById(R.id.msg);
        status= (TextView) findViewById(R.id.status);
        writeMsg= (EditText) findViewById(R.id.writemsg);
        listDevices= (Button) findViewById(R.id.listDevice);
    }

    /**
     * enables server to await connections
     */
    private class ServerClass extends Thread
    {
        private BluetoothServerSocket serverSocket;

        public ServerClass(){
            try {
                // creates server socket with hardcoded uuid
                serverSocket=bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            BluetoothSocket socket=null;

            while (socket==null)// loops while null connection
            {
                try {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);

                    socket=serverSocket.accept(); // enable socket connect
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(socket!=null) // when connected...
                {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendRecieve=new SendRecieve(socket);
                    sendRecieve.start();// create send recieve with new serverSocket

                    break;
                }
            }
        }
    }

    /**
     * enables connection from client side
     */
    private class ClientClass extends Thread
    {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass (BluetoothDevice device1)
        {
            device=device1; // device sent through param
//            device = bluetoothAdapter.getRemoteDevice("48:01:C5:33:C3:0D");// hard code device phone for testing(works)
            try {
                socket=device.createRfcommSocketToServiceRecord(MY_UUID); // create client side socket with device
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            try {
                socket.connect(); // connects
                Message message=Message.obtain();
                message.what=STATE_CONNECTED;
                handler.sendMessage(message);

                sendRecieve=new SendRecieve(socket); // enables send and receive with socket
                sendRecieve.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    /**
     * send and recieve messages
     */
    private class SendRecieve extends Thread{
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        /**
         * creates input and output stream
         * @param socket
         */
        public SendRecieve (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream=tempIn;
            outputStream=tempOut;
        }

        /**
         * infinate loop of creating bytes and sending it to the handler to display message
         */
        public void run()
        {
            byte[] buffer=new byte[1024];
            int bytes;

            while (true)
            {
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        /**
         * outputs message
         * @param bytes
         */
        public void write(byte[] bytes)
        {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}