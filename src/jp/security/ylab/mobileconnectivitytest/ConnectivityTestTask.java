package jp.security.ylab.mobileconnectivitytest;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;

public class ConnectivityTestTask extends AsyncTask<Void, Integer, Void>{
	//private final static String kTestServerAddress = "133.34.143.107";//実験表のIP，ソース公開時は伏字にする
	private final static String[] kTestServerNames = {"<dummy1>","<dummy2>"};
	private final static int[] kTargetTCPPorts = {21,22,23,25,42,53,80,110,135,137,138,139,443,445,1433,3127,3389,5060,5061,
		50220,50221};
	private final static int[] kTargetUDPPorts = {53,69,123,137,138,139,1434,5060};
	//Androidがlistenするポート　できれば使っていないポートを調べる処理を実行して
	//確実なポートを自動選択できるようにしたいが，とりあえず決め打ち
	private final static int[] kOpenTCPPorts = {30217,50220};
	//private final static int[] kOpenUDPPorts = {8080,30217};
	
	//たぶんプロバイダとうにブロックされないであろうポート．根拠はない
	private final static int[] kHighConnectivityTCPPorts = {50220,50221};

	private final static int kAndroidServerWaitTimeMill = 15000;
	private final static int kAndroidServerConnectionTimeMill = 10000;
	
	Map<String, String> networkEnvHashMap = new HashMap<String, String>();
	private ProgressDialog dialog;
	private Button startButton;
	TelephonyManager tm;
	ConnectivityManager cm;
	Context context;

	public ConnectivityTestTask(Context context,ProgressDialog dialog,Button startButton)
	{
		super();
		this.context = context;
		this.dialog = dialog;
		this.startButton = startButton;
		cm = (ConnectivityManager)context.getSystemService( Context.CONNECTIVITY_SERVICE );
		tm = (TelephonyManager)context.getSystemService( Context.TELEPHONY_SERVICE );
	}
	
	//実行直前の処理
	@Override
	protected void onPreExecute() {
		// TODO Auto-generated method stub
		super.onPreExecute();
		startButton.setEnabled(false);
		dialog = new ProgressDialog(context);
		dialog.setIndeterminate(false);
		dialog.setCancelable(false);
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setMessage("Now testing...");
		dialog.show();
	}
	@Override
	protected void onProgressUpdate(Integer... values) {
		// TODO Auto-generated method stub
		dialog.incrementProgressBy(values[0]);
	}
	//バックグラウンド処理終了時
	@Override
	protected void onPostExecute(Void result) {
		// TODO Auto-generated method stub
		super.onPostExecute(result);
		if(dialog != null && dialog.isShowing())
		{
			dialog.dismiss();
			dialog = null;
		}
		startButton.setEnabled(true);
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
		alertDialog.setMessage(R.string.end_of_test);
		alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				
			}
		}).show();
		
	}
	
	@Override
	protected void onCancelled() {
		// TODO Auto-generated method stub
		super.onCancelled();
		if(dialog != null && dialog.isShowing())
		{
			dialog.dismiss();
			dialog = null;
		}
		
	}
	

	//処理実行，別スレッドで行わる．そのためこの中でUIは操作できない
	@Override
	protected Void doInBackground(Void... params) {
		// TODO Auto-generated method stub
		//プログレスの最大値．「１」はwifiオフ作業の分
		int progressMax = (kTargetTCPPorts.length + kTargetUDPPorts.length + 
				kOpenTCPPorts.length)*2  + 1;
		WifiManager wifiMgr= (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		
		dialog.setMax(progressMax);

		//wifiがONならOFFにして少し待つ
		if(wifiMgr.isWifiEnabled())
		{
			State mobileState = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
			wifiMgr.setWifiEnabled(false);
			try {
				for(int i=0;i<60;i++)
				{
					Thread.sleep(1000);
					mobileState = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
					//モバイル接続が確認できたら試験をすぐに開始する
					//問題はwimaxなどを標準で利用している場合．この場合mobileがconnectedにならないことがあるようだ
					if(mobileState == State.CONNECTED) break;
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		publishProgress(1);

		//ネットワーク情報の取得
		if(getNetworkEnvInfo())
		{
			for(int j=0;j<kTestServerNames.length;j++)
			{
				for(int i=0;i<kTargetTCPPorts.length;i++)
				{
					sendTCPPacket(kTestServerNames[j],kTargetTCPPorts[i],false);
					//portが80番のときは第3引数をtrue,false２種類の送信を行うこと!!!
					if(kTargetTCPPorts[i] == 80)
					{
						sendTCPPacket(kTestServerNames[j],kTargetTCPPorts[i],true);
					}
					publishProgress(1);
				}
				for(int i=0;i<kTargetUDPPorts.length;i++)
				{
					sendUDPPacket(kTestServerNames[j],kTargetUDPPorts[i]);
					publishProgress(1);
				}
				for(int i=0;i<kOpenTCPPorts.length;i++)
				{
					Log.d("debug","before call becomeTCPServerTest");
					try {
						becomeTCPServerTest(kTestServerNames[j],kOpenTCPPorts[i]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						Log.d("debug","IOExceptionによりbecomeTCPServerTestは中断されました");
					}
					publishProgress(1);
				}
			}
		}
		else
		{
			//!!メインスレッドでないためUIの操作ができない．何らかの方法でUIを別に変更する!!
			//Toast.makeText(this, "failed", Toast.LENGTH_SHORT).show();
			Log.d("debug","getNetworkInfo is false");
		}

		return null;
	}
	private void becomeTCPServerTest(String serverName,int port) throws IOException
	{

		boolean notified = true;
		Log.d("debug","in becomeTCPServerTets");
		ServerSocket ss = null;
		Socket sock;
		String sendString = "I am Android,";
		sendString += String.valueOf(port);
		//導通確認用ホストに開けるポート番号を通知
		for(int i=0;i<kHighConnectivityTCPPorts.length;i++)
		{
			Log.d("debug","Try notify TestHost port: " + String.valueOf(kHighConnectivityTCPPorts[i]));
			try {
				sock = new Socket();
				sock.connect(new InetSocketAddress(serverName, kHighConnectivityTCPPorts[i]),3000);
				DataOutputStream out = new DataOutputStream(sock.getOutputStream());
				out.write(sendString.getBytes("ASCII"));
				Log.d("debug","Notified port: "+ String.valueOf(kHighConnectivityTCPPorts[i]));
				notified = true;
				sock.close();
				break;
			} catch(SocketTimeoutException ste)
			{
				Log.d("debug","SocketTimeoutException");
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(!notified)
		{
			Log.d("debug","Android couldn't notify");
			return;
		}
		try {
			Log.d("debug","new ServerSocket:"+ String.valueOf(port));
			ss = new ServerSocket();
			ss.setReuseAddress(true);
			ss.bind(new InetSocketAddress(port));
			Log.d("debug","tcp port " + String.valueOf(port) + " でサーバ化");
			ss.setSoTimeout(kAndroidServerWaitTimeMill);
			Socket clientSocket = ss.accept();
			Log.d("debug","tcp port " + String.valueOf(port) + " に対しての接続を確認");
			clientSocket.setSoTimeout(kAndroidServerConnectionTimeMill);
			//InputStream in = clientSocket.getInputStream();
			OutputStream out = clientSocket.getOutputStream();
			out.write("I am Android Server".getBytes());
			Log.d("debug","tcp port " + String.valueOf(port) + " で文字列を送信した");
			clientSocket.close();
			ss.close();

		}catch(SecurityException se) 
		{
			Log.d("debug","SecurityException");
		}
		catch(SocketTimeoutException ste)
		{
			Log.d("debug","サーバ化したが接続がなかった（時間切れ）");
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			if(ss != null)
			{
				Log.d("debug","finallyでssをclose");
				ss.close();
			}
		}
		Log.d("debug","End of becomeTCPServerTest");


	}
	
	/*need?
	private void becomeUDPServerTest(int port)
	{
	}
	*/

	private boolean getNetworkEnvInfo()
	{
		State wifiState = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		State mobileState = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
		if(wifiState == State.CONNECTED)
		{
			//!!not implmented now !!Wifi接続されているときは切るように促すもしくは自動的に切断する処理をいれる 
			return false;
		}
		else 
		{
			//この時にもしmobile接続がofｆになっている場合（wimaxなどを使うとwifi,mobileともに
			//OFFという認識になる？（要確認）
			if(mobileState == State.CONNECTED)
			{
				networkEnvHashMap.put("mobileState","on");
				networkEnvHashMap.put("CountoryIso",tm.getNetworkCountryIso());
				networkEnvHashMap.put("OperatorID",tm.getNetworkOperator());
				networkEnvHashMap.put("OperatorName",tm.getNetworkOperatorName());
				networkEnvHashMap.put("IP",getIPAddress());
			}
			//wifiもmobileもoff,wimaxなどを使っているとこうなるときがある
			else
			{
				networkEnvHashMap.put("mobileState","off");
				networkEnvHashMap.put("CountoryIso",tm.getNetworkCountryIso());
				networkEnvHashMap.put("OperatorID",tm.getNetworkOperator());
				networkEnvHashMap.put("OperatorName",tm.getNetworkOperatorName());
				networkEnvHashMap.put("IP",getIPAddress());
			}

		}
		System.out.println(networkEnvHashMap);//for debug
		return true;
	}
	private String getIPAddress()
	{
		Enumeration<NetworkInterface> enuNic;
		String ret = "";
		try {
			enuNic = NetworkInterface.getNetworkInterfaces();
			if(enuNic != null)
			{
				while(enuNic.hasMoreElements())
				{
					NetworkInterface ni = (NetworkInterface)enuNic.nextElement();
					Enumeration<InetAddress> enuAddress = ni.getInetAddresses();
					while(enuAddress.hasMoreElements())
					{
						InetAddress in4 = (InetAddress)enuAddress.nextElement();
						Log.d("debug","getHostAddress " + in4.getHostAddress());
						if(in4.getHostAddress() != "127.0.0.1") ret += in4.getHostAddress() + ",";
					}
				}
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ret;
	}
	private void sendTCPPacket(String ip,int port,boolean isHttp)
	{
		Socket sock = new Socket();
		String sendString = "";
		try {
			sock.connect(new InetSocketAddress(ip, port),7000);
			DataOutputStream out = new DataOutputStream(sock.getOutputStream());
			for(Map.Entry<String, String> entry:networkEnvHashMap.entrySet())
			{
				sendString += entry.getKey() + " : " + entry.getValue() + ",";
			}
			if(isHttp)
			{
				//プロキシサーバに対応されるような通信
				sendString = makeDummyGetRequest(ip, sendString);
			}
			out.write(sendString.getBytes("ASCII"));
			sock.close();
		} catch(SocketTimeoutException ste)
		{
			Log.d("debug","SocketTimeoutException");
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void sendUDPPacket(String serverName,int port)
	{
		try {
			DatagramSocket sock = new DatagramSocket();
			String sendString = "I am Android\n";
			//単純に通信環境情報を送るだけの通信	
			for(Map.Entry<String, String> entry:networkEnvHashMap.entrySet())
			{
				sendString += entry.getKey() + " : " + entry.getValue() + ",";
			}
			DatagramPacket packet = new DatagramPacket(sendString.getBytes("ASCII"),sendString.getBytes("ASCII").length,new InetSocketAddress(serverName, port));
			sock.send(packet);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}
	private String makeDummyGetRequest(String serverName,String netInfo)
	{
		String ret = "";
		ret += "GET / HTTP/1.1\nHost: ";
		ret += serverName;
		ret += "\nAccept-Encoding: gzip\nAccept-Language: en-US\n" +
				"Accept: application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5\n" +
				"User-Agent: Mozilla/5.0 (Linux; U; Android 2.2; en-us; sdk Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1" +
				"Accept-Charset: utf-8, iso-8859-1, utf-16, *;q=0.7" +
				"Net-Info: ";
		ret += netInfo;
		ret += "\n\n";

		return ret;
	}

}
