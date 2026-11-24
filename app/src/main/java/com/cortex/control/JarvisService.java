package com.cortex.control;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.speech.*;
import java.util.*;

public class JarvisService extends Service {
 private static final String CHANNEL="cortex_jarvis";
 private SpeechRecognizer recognizer;
 private Handler handler;
 private boolean listening=false, restarting=false;
 @Override public void onCreate(){super.onCreate();handler=new Handler(Looper.getMainLooper());createChannel();}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null && "STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
  startForeground(701,notification());listening=true;listenAgain(250);return START_NOT_STICKY;
 }
 private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"JARVIS — голосовой режим",NotificationManager.IMPORTANCE_LOW);c.setDescription("Показывает, когда JARVIS использует микрофон");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
 private Notification notification(){
  Intent stop=new Intent(this,JarvisService.class).setAction("STOP");
  PendingIntent pi=PendingIntent.getService(this,702,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
  return b.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS активен").setContentText("Ожидаю «Jarvis» • микрофон включён").setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Выключить",pi).build();
 }
 private void listenAgain(long delay){
  if(!listening||restarting)return;restarting=true;
  handler.postDelayed(()->{
   restarting=false;if(!listening)return;
   try{
    if(!SpeechRecognizer.isRecognitionAvailable(this)){stopSelf();return;}
    if(recognizer!=null){try{recognizer.destroy();}catch(Exception ignored){}}
    recognizer=SpeechRecognizer.createSpeechRecognizer(this);
    recognizer.setRecognitionListener(new RecognitionListener(){
     public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float x){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
     public void onError(int e){listenAgain(1000);}
     public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty())handlePhrase(a.get(0));listenAgain(500);}
     public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){}
    });
    Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
    i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
    recognizer.startListening(i);
   }catch(Exception e){listenAgain(1500);}
  },delay);
 }
 private void handlePhrase(String phrase){
  String p=phrase.toLowerCase(Locale.ROOT).trim();int at=p.indexOf("джарвис");int len=7;
  if(at<0){at=p.indexOf("jarvis");len=6;}if(at<0)return;
  String cmd=p.substring(at+len).replaceFirst("^[ ,.!?]+","").trim();if(cmd.isEmpty())return;
  if(cmd.startsWith("домой")||cmd.equals("на главный экран")){
   Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(home);return;
  }
  if(cmd.startsWith("открой ")||cmd.startsWith("запусти ")){
   String label=cmd.substring(cmd.indexOf(' ')+1).trim();Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
   for(android.content.pm.ResolveInfo ri:getPackageManager().queryIntentActivities(q,0)){
    CharSequence n=ri.loadLabel(getPackageManager());if(n!=null&&n.toString().equalsIgnoreCase(label)){
     Intent open=getPackageManager().getLaunchIntentForPackage(ri.activityInfo.packageName);if(open!=null){open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(open);}return;
    }
   }
  }
 }
 @Override public void onDestroy(){listening=false;if(handler!=null)handler.removeCallbacksAndMessages(null);if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}}stopForeground(true);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}