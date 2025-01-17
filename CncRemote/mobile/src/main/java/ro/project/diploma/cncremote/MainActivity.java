package ro.project.diploma.cncremote;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    public final Context context = this;

    private static String IP;
    private ConnectivityManager connManager;
    private RequestQueue requestsQueue;
    private TextView coordsX;
    private TextView coordsY;
    private TextView coordsZ;
    private TextView incrementScaleView;
    private TextView incrementSpeedView;
    private Button zeroMachine;
    private Switch toggle;
    private StringRequest getSpindleRequest;
    private Double incrementScale = 1.0;
    private Integer delayBetweenSteps = 300; // minimum working delay!

    private float mLastX, mLastY, mLastZ;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private final float NOISE = (float) 2.0;

    private Timer timerUpdateSensorCoords;
    private TimerTask updateSensorCoords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        requestsQueue = Volley.newRequestQueue(context);

        connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Timer timerSpindlePosition=new Timer();
        TimerTask getSpindlePosition=new TimerTask() {
            public void run() {
                if(IP!=null && getSpindleRequest!=null)
                    requestsQueue.add(getSpindleRequest);
            }};
        timerSpindlePosition.schedule(getSpindlePosition,0,100);

        coordsX = (TextView) findViewById(R.id.text_X);
        coordsY = (TextView) findViewById(R.id.text_Y);
        coordsZ = (TextView) findViewById(R.id.text_Z);

        incrementScaleView = (TextView)findViewById(R.id.incrementScale);
        incrementScaleView.setText(""+incrementScale);
        incrementScaleView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String str = incrementScaleView.getText().toString();
                if (str.equals("")) {
                    incrementScaleView.setText("1.0");
                    Toast.makeText(context, ";)", Toast.LENGTH_SHORT).show();
                }
            }
        });

        incrementSpeedView = (TextView)findViewById(R.id.speedScale);
        incrementSpeedView.setText(""+delayBetweenSteps);
        incrementSpeedView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String str = incrementSpeedView.getText().toString();
                if (!str.equals("")) {
                    delayBetweenSteps = Integer.parseInt(str);
                    if (delayBetweenSteps < 60) {
                        incrementSpeedView.setText("300");
                        Toast.makeText(context, "sorry, can not set a value lower than 60, \neven for 60 the Z-axe can hardly handle it!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    incrementSpeedView.setText("300");
                    Toast.makeText(context, ";)", Toast.LENGTH_SHORT).show();
                }
            }
        });

        zeroMachine = (Button) findViewById(R.id.button_ZeroMachine);
        zeroMachine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doGet(IP + "/CNC/GUI?load=whatever&speed=" + delayBetweenSteps + "&action=zeroMachine");
            }
        });

        toggle = (Switch) findViewById(R.id.spindle);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Switch tg = (Switch) findViewById(R.id.spindle);
                if (IP != null)
                    if (!toggle.isChecked()) {
                        doGet(IP + "/CNC/GUI?load=whatever&action=toggleSpindle&state=off");
                        toggle.setChecked(false);
                    }
                    else {
                        doGet(IP + "/CNC/GUI?load=whatever&action=toggleSpindle&state=on");
                        toggle.setChecked(true);
                    }
                else
                    toggle.setChecked(false);
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().getType() != ConnectivityManager.TYPE_WIFI) {
            Toast.makeText(context, "Please connect to the network to access the machine via IP Address", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!findViewById(R.id.button_sensors).isEnabled())
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        controlMode(findViewById(R.id.button_manual));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.config, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.set_IP:
                if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().getType() != ConnectivityManager.TYPE_WIFI) {
                    Toast.makeText(context, "Please connect to the network to access the machine via IP Address", Toast.LENGTH_LONG).show();
                } else {
                    IP = null;
                    getSpindleRequest = null;
                    requestsQueue.cancelAll(Request.Priority.NORMAL);
                    coordsX.setText("");
                    coordsY.setText("");
                    coordsZ.setText("");

                    LayoutInflater layoutInflater = LayoutInflater.from(context);

                    View promptView = layoutInflater.inflate(R.layout.ip, null);

                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);

                    alertDialogBuilder.setView(promptView);

                    final EditText input = (EditText) promptView.findViewById(R.id.ip_address);

                    alertDialogBuilder
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // get user input and set it to result
                                    IP = "http://" + input.getText().toString();
                                    doGet(IP + "/CNC/GUI");
                                    getSpindleRequest = new StringRequest(Request.Method.GET, IP + "/CNC/GUI?load=whatever&action=getSpindlePosition",
                                            new Response.Listener<String>() {
                                                @Override
                                                public void onResponse(String response) {
                                                    if (IP!=null) {
                                                        String[] coords = response.split("#");
                                                        if (coords.length > 3) {
                                                            coordsX.setText(coords[0]);
                                                            coordsY.setText(coords[1]);
                                                            coordsZ.setText(coords[2]);
                                                            if (coords[3].equals("on")){
                                                                toggle.setChecked(true);
                                                            } else if (coords[3].equals("off")) {
                                                                toggle.setChecked(false);
                                                            }
                                                        }
                                                    }
                                                    // Display the first 500 characters of the response string.
                                                    //Log.d("Response is: ", response.substring(0, 500));
                                                    //Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show();
                                                }
                                            }, new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            Log.d("Request : ", IP + "/CNC/GUI?load=whatever&action=getSpindlePosition" + " That didn't work!");
                                            Toast.makeText(context, IP + "/CNC/GUI?load=whatever&action=getSpindlePosition" + " That didn't work!", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            })
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    });

                    // create an alert dialog
                    AlertDialog alertD = alertDialogBuilder.create();

                    alertD.show();
                    return true;
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mLastX = event.values[0];
        mLastY = event.values[1];
        mLastZ = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing ATM
    }

    public void alterPosition(View v) {
        if (IP != null)
        {
            String incrementStr = incrementScaleView.getText().toString();
            switch (v.getId()) {
                case R.id.button_Xminus:
                    doGet(IP + "/CNC/GUI?load=whatever&action=alterPosition&speed=" + delayBetweenSteps + "&incrementScale=" + incrementStr + "&axis=X&dir=minus&currX=" + coordsX.getText().toString() + "&currY=" + coordsY.getText().toString() + "&currZ=" + coordsZ.getText().toString());
                    break;
                case R.id.button_Xplus:
                    doGet(IP + "/CNC/GUI?load=whatever&action=alterPosition&speed=" + delayBetweenSteps + "&incrementScale=" + incrementStr + "&axis=X&dir=plus&currX=" + coordsX.getText().toString() + "&currY=" + coordsY.getText().toString() + "&currZ=" + coordsZ.getText().toString());
                    break;
                case R.id.button_Yminus:
                    doGet(IP + "/CNC/GUI?load=whatever&action=alterPosition&speed=" + delayBetweenSteps + "&incrementScale=" + incrementStr + "&axis=Y&dir=minus&currX=" + coordsX.getText().toString() + "&currY=" + coordsY.getText().toString() + "&currZ=" + coordsZ.getText().toString());
                    break;
                case R.id.button_Yplus:
                    doGet(IP + "/CNC/GUI?load=whatever&action=alterPosition&speed=" + delayBetweenSteps + "&incrementScale=" + incrementStr + "&axis=Y&dir=plus&currX=" + coordsX.getText().toString() + "&currY=" + coordsY.getText().toString() + "&currZ=" + coordsZ.getText().toString());
                    break;
                case R.id.button_Zminus:
                    doGet(IP + "/CNC/GUI?load=whatever&action=alterPosition&speed=" + delayBetweenSteps + "&incrementScale=" + incrementStr + "&axis=Z&dir=minus&currX=" + coordsX.getText().toString() + "&currY=" + coordsY.getText().toString() + "&currZ=" + coordsZ.getText().toString());
                    break;
                case R.id.button_Zplus:
                    doGet(IP + "/CNC/GUI?load=whatever&action=alterPosition&speed=" + delayBetweenSteps + "&incrementScale=" + incrementStr + "&axis=Z&dir=plus&currX=" + coordsX.getText().toString() + "&currY=" + coordsY.getText().toString() + "&currZ=" + coordsZ.getText().toString());
                    break;
            }
        }
    }

    public void setScale(View v) {
        String str = incrementScaleView.getText().toString();
        if (!str.equals("")) {
            incrementScale = Double.parseDouble(str);
            switch (v.getId()) {
                case R.id.scalePlus:
                    incrementScale++;
                    break;
                case R.id.scaleMinus:
                    if (incrementScale > 1)
                        incrementScale--;
                    break;
            }
            incrementScaleView.setText(incrementScale.toString());
        }
    }

    public void setSpeed(View v) {
        String str = incrementSpeedView.getText().toString();
        if (!str.equals("")) {
            delayBetweenSteps = Integer.parseInt(str);
            switch (v.getId()) {
                case R.id.speedPlus:
                    delayBetweenSteps+=10;
                    break;
                case R.id.speedMinus:
                    if (delayBetweenSteps > 60)
                        delayBetweenSteps-=10;
                    break;
            }
            incrementSpeedView.setText(delayBetweenSteps.toString());
        }
    }

    private void doGet(final String url) {
        //*
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        //Log.d("Response is: ", response.substring(0, 500));
                        //Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (url.contains("toggleSpindle")){
                    toggle.setChecked(false);
                }
                requestsQueue.cancelAll(Request.Priority.NORMAL);
                Log.d("Request : ", "That did NOT work for " + url);
                Toast.makeText(context, "That did NOT work for " + url, Toast.LENGTH_SHORT).show();
            }
        });
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
        //*/
    }

    public void controlMode(View v) {
        switch (v.getId()) {
            case R.id.button_manual:
                mSensorManager.unregisterListener(this);

                if (timerUpdateSensorCoords != null && updateSensorCoords != null) {
                    timerUpdateSensorCoords.cancel();
                    updateSensorCoords.cancel();
                    updateSensorCoords = null;
                    timerUpdateSensorCoords.purge();
                    timerUpdateSensorCoords = null;
                }

                findViewById(R.id.button_manual).setEnabled(false);
                findViewById(R.id.button_sensors).setEnabled(true);

                findViewById(R.id.button_Xminus).setEnabled(true);
                findViewById(R.id.button_Xplus).setEnabled(true);
                findViewById(R.id.button_Yminus).setEnabled(true);
                findViewById(R.id.button_Yplus).setEnabled(true);
                findViewById(R.id.button_Zminus).setEnabled(true);
                findViewById(R.id.button_Zplus).setEnabled(true);
                findViewById(R.id.button_ZeroMachine).setEnabled(true);
                findViewById(R.id.scaleMinus).setEnabled(true);
                findViewById(R.id.scalePlus).setEnabled(true);

                findViewById(R.id.incrementScale).setEnabled(true);

                break;
            case R.id.button_sensors:
                mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

                timerUpdateSensorCoords = new Timer();
                updateSensorCoords = new TimerTask() {
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("Coords\n", "X: " + mLastX + "\nY: " + mLastY + "\nZ: " + mLastZ + "");
                                if (IP != null) {
                                    incrementScale = 1.0;
                                    incrementScaleView.setText("1");
                                    if (mLastX > NOISE) {
                                        alterPosition(findViewById(R.id.button_Xplus));
                                    } else if (mLastX < -NOISE) {
                                        alterPosition(findViewById(R.id.button_Xminus));
                                    } else if (mLastY > NOISE) {
                                        alterPosition(findViewById(R.id.button_Yminus));
                                    } else if (mLastY < -NOISE) {
                                        alterPosition(findViewById(R.id.button_Yplus));
                                    }
                                }
                            }
                        });
                    }
                };
                timerUpdateSensorCoords.schedule(updateSensorCoords,0,1000);

                findViewById(R.id.button_manual).setEnabled(true);
                findViewById(R.id.button_sensors).setEnabled(false);

                findViewById(R.id.button_Xminus).setEnabled(false);
                findViewById(R.id.button_Xplus).setEnabled(false);
                findViewById(R.id.button_Yminus).setEnabled(false);
                findViewById(R.id.button_Yplus).setEnabled(false);
                findViewById(R.id.button_Zminus).setEnabled(false);
                findViewById(R.id.button_Zplus).setEnabled(false);
                findViewById(R.id.button_ZeroMachine).setEnabled(false);
                findViewById(R.id.scaleMinus).setEnabled(false);
                findViewById(R.id.scalePlus).setEnabled(false);

                findViewById(R.id.incrementScale).setEnabled(false);

                break;
        }
    }

}
