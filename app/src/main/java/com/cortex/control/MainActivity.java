package com.cortex.control;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.widget.*;
import android.view.View;
import java.util.*;

public class MainActivity extends Activity {
 TextView status; EditText command; SpeechRecognizer recognizer;
 @Override public void onCreate(Bundle b){super.onCreate(b); LinearLayout l=new LinearLayout(this); l.setOrientation(1); l.setPadding(32,48,32,32); l.setBackgroundColor(0xff07111f);
 TextView title=new TextView(this); title.setText("CORTEX CONTROL"); title.setTextColor(0xff55ddff); title.setTextSize(26); l.addView(title);
 TextView hint=new TextView(this); hint.setText("Голосовое управление телефоном"); hint.setTextColor(-1); l.addView(hint);
 command=new EditText(this); command.setHint("Например: Напиши контакту Папуля, я тебя люблю"); command.setTextColor(-1); command.setHintTextColor(0xff9aaabd); l.addView(command);
 Button mic=new Button(this); mic.setText("🎙 Слушать команду"); l.addView(mic);
 Button run=new Button(this); run.setText("Выполнить"); l.addView(run);
 status=new TextView(this); status.setTextColor(0xff55ddff); status.setText("Команды выполняются только на этом телефоне."); l.addView(status); setContentView(l);
 mic.setOnClickListener(v->listen()); run.setOnClickListener(v->handle(command.getText().toString()));
 }
 void listen(){ if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("Распознавание речи недоступно");return;} if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;} recognizer=SpeechRecognizer.createSpeechRecognizer(this); recognizer.setRecognitionListener(new RecognitionListener(){public void onReadyForSpeech(Bundle b){status.setText("Слушаю...");} public void onBeginningOfSpeech(){} public void onRmsChanged(float x){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onError(int e){status.setText("Не удалось распознать речь. Попробуй ещё раз.");} public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()){command.setText(a.get(0));handle(a.get(0));}} public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){} }); Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU"); recognizer.startListening(i); }
 void handle(String s){String t=s.trim(); String low=t.toLowerCase(Locale.ROOT); if(low.startsWith("напиши контакту ")){String rest=t.substring("напиши контакту ".length()); int comma=rest.indexOf(','); if(comma<0){status.setText("Скажи: Напиши контакту Папуля, я тебя люблю");return;} String name=rest.substring(0,comma).trim(); String body=rest.substring(comma+1).trim(); findContact(name,body);return;} if(low.startsWith("открой ")||low.startsWith("запусти ")){String app=t.substring(t.indexOf(' ')+1).trim(); Intent launch=getPackageManager().getLaunchIntentForPackage(resolvePackage(app)); if(launch!=null){startActivity(launch);status.setText("Открываю "+app);}else status.setText("Не нашёл приложение: "+app);return;} if(low.equals("домой")){startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));return;} status.setText("Команда не распознана. Попробуй: открой YouTube или напиши контакту Папуля, я тебя люблю."); }
 String resolvePackage(String label){android.content.Intent i=new android.content.Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER); for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(i,0)){CharSequence n=r.loadLabel(getPackageManager());if(n!=null&&n.toString().equalsIgnoreCase(label))return r.activityInfo.packageName;}return "";}
 void findContact(String name,String body){if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},2);status.setText("Разреши доступ к контактам и повтори команду.");return;} ArrayList<String> numbers=new ArrayList<>(); try(android.database.Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" LIKE ?",new String[]{"%"+name+"%"},null)){while(c!=null&&c.moveToNext())numbers.add(c.getString(0));}catch(Exception e){status.setText("Не удалось прочитать контакты");return;} if(numbers.isEmpty()){status.setText("Контакт «"+name+"» не найден.");return;} if(numbers.size()==1){compose(numbers.get(0),body);return;} new AlertDialog.Builder(this).setTitle("Выбери номер контакта").setItems(numbers.toArray(new String[0]),(d,w)->compose(numbers.get(w),body)).show(); }
 void compose(String number,String body){Intent i=new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"+Uri.encode(number)));i.putExtra("sms_body",body);try{startActivity(i);status.setText("Проверь сообщение и нажми «Отправить» в SMS-приложении.");}catch(Exception e){status.setText("Не удалось открыть SMS-приложение.");}}
 @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();super.onDestroy();}
}
