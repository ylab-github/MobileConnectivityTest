package jp.security.ylab.mobileconnectivitytest;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.ProgressDialog;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MobileConnectivityTest extends Activity implements OnClickListener {

	private final static String kPASS = "<dummy3>";//ソース公開時は伏字にする

	private ProgressDialog dialog;
	private ConnectivityTestTask connectivityTestTask;
	Button startButton;
	EditText passEditText;
	TextView helloText;
	Map<String, String> networkEnvHashMap = new HashMap<String, String>();
	TelephonyManager tm;
	ConnectivityManager cm;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mobile_connectivity_test);
		startButton = (Button)findViewById(R.id.button1);
		passEditText = (EditText)findViewById(R.id.editText1);
		startButton.setOnClickListener(this);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_check_network_env, menu);
		return true;
	} 

	private void startCheck() 
	{
		Log.d("debug",passEditText.getText().toString());
		if(passEditText.getText().toString().equals(kPASS))
		{
			connectivityTestTask = new ConnectivityTestTask(this,dialog,startButton);
			connectivityTestTask.execute();

		}
		else
		{
			Toast.makeText(this, R.string.pass_is_not_correct, Toast.LENGTH_SHORT).show();
		}
	}
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if(v.getId() == startButton.getId())
		{
			startCheck();
		}

	}
}
