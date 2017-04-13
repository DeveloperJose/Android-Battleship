// Author: Jose Perez <josegperez@mail.com> and Diego Reynoso
package edu.utep.cs.cs4330.battleship.activity;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import edu.utep.cs.cs4330.battleship.AcceptThread;
import edu.utep.cs.cs4330.battleship.ConnectedThread;
import edu.utep.cs.cs4330.battleship.DeployAIFragment;
import edu.utep.cs.cs4330.battleship.DeployMultiFragment;
import edu.utep.cs.cs4330.battleship.MessageConstants;
import edu.utep.cs.cs4330.battleship.NetworkType;
import edu.utep.cs.cs4330.battleship.R;
import edu.utep.cs.cs4330.battleship.ai.StrategyType;
import edu.utep.cs.cs4330.battleship.model.GameType;
import edu.utep.cs.cs4330.battleship.model.Ship;
import edu.utep.cs.cs4330.battleship.view.DeploymentBoardView;

public class DeploymentActivity extends AppCompatActivity implements DeploymentBoardView.DeploymentListener {

    private TextView textGamemode;
    private TextView textDeployStatus;
    private DeploymentBoardView boardViewDeployment;
    private Switch switchRotation;
    private RadioGroup radioGroupStrategy;
    private Button buttonDeploy;
    private TextView textDeployProgress;
    private ProgressBar progressBarDeploy;


    private GameType gameType;
    private Fragment fragmentDeployment;
    private boolean isFragmentReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setContentView(R.layout.activity_deployment);

        textGamemode = (TextView) findViewById(R.id.textDepGamemode);
        boardViewDeployment = (DeploymentBoardView) findViewById(R.id.boardViewDeployment);
        buttonDeploy = (Button) findViewById(R.id.btnDeploy);
        switchRotation = (Switch) findViewById(R.id.switch_rotation);
        textDeployStatus = (TextView) findViewById(R.id.textDeployStatus);
        textDeployProgress = (TextView) findViewById(R.id.textDeployProgress);
        progressBarDeploy = (ProgressBar) findViewById(R.id.progressBarDeployment);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            gameType = (GameType)extras.get(getString(R.string.main_menu_intent_gamemode));

            if(gameType == GameType.Singleplayer){
                textGamemode.setText(getString(R.string.main_menu_singleplayer_description));
                fragmentDeployment = new DeployAIFragment();
            }
            else{
                textGamemode.setText(getString(R.string.main_menu_multiplayer_description));
                fragmentDeployment = new DeployMultiFragment();
            }

            // Add Fragment to UI
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, fragmentDeployment);
            transaction.commit();

        }

        boardViewDeployment.addListener(this);

        switchRotation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boardViewDeployment.rotationMode = isChecked;
            }
        });

        updateDeployStatus();
    }

    @Override
    public void onShipDeployed(Ship ship) {
        updateDeployStatus();
    }

    public void updateDeployStatus() {
        int shipTotal = boardViewDeployment.getRemainingShips();

        if (shipTotal == 0)
            textDeployStatus.setText(getString(R.string.deployment_all_ships));
        else
            textDeployStatus.setText(getString(R.string.deployment_remaining_ships) + shipTotal);

        checkReady();
    }

    public void onFragmentUpdate(boolean isFragmentReady){
        this.isFragmentReady = isFragmentReady;
        checkReady();
    }

    public void checkReady() {
        int shipTotal = boardViewDeployment.getRemainingShips();
        boolean isReady = (shipTotal == 0 && isFragmentReady);
        buttonDeploy.setEnabled(isReady);
    }

    public void onClickReset(View v) {
        boardViewDeployment.onCreate();
        updateDeployStatus();
        checkReady();
    }

    private BluetoothAdapter bluetoothAdapter;
    private int REQUEST_DISCOVER = 15;
    public void onClickDeploy(View v) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(getString(R.string.deployment_intent_board), boardViewDeployment.getBoard());

        // Single-player VS AI game
        if(gameType == GameType.Singleplayer){
            // Get the strategy from the fragment
            DeployAIFragment fragment = (DeployAIFragment)fragmentDeployment;
            i.putExtra(getString(R.string.deployment_intent_strategy), fragment.strategyAI);
            startActivity(i);
        }
        // Multi-player VS Human game
        else{
            DeployMultiFragment fragment = (DeployMultiFragment)fragmentDeployment;
            NetworkType networkType = fragment.networkType;
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // Hosting
            if(networkType == NetworkType.Host){
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                // 300 seconds = 5 minutes
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivityForResult(intent, REQUEST_DISCOVER);
                progressBarDeploy.setIndeterminate(true);
                textDeployProgress.setText("Waiting for client");
            }
            // Client
            else{

            }
        }


    }

    private Handler connectedHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == MessageConstants.MESSAGE_CONNECTED){
                Log.d("Debug", "Game to start now!");
                progressBarDeploy.setIndeterminate(false);
                textDeployProgress.setText("Connected!");
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_DISCOVER){
            if(resultCode == 300){
                Thread accept = new AcceptThread(this, connectedHandler, bluetoothAdapter);
                accept.start();
                ConnectedThread.setHandler(connectedHandler);
            }
            else{
                progressBarDeploy.setIndeterminate(false);
                textDeployProgress.setText("Waiting for deployment");
            }
        }
    }
}
