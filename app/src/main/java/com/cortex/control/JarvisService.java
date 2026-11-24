package com.cortex.control;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.speech.*;
import android.widget.Toast;
import java.text.Normalizer;
import java.util.*;

public class JarvisService extends Service {
    private static final String CHANNEL="cortex_jarvis";
    private SpeechRecognizer recognizer;
    private Handler handler;
    private boolean listening=false, restarting=false;

    @Override public void onCreate(){ super.onCreate(); handler=new Handler(Looper.getMainLooper()); createChannel(); }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && "STOP".equals(intent.getAction())){ stopSelf(); return START_NOT_STICKY; }
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ stopSelf(); return START_NOT_STICKY; }
        startForeground(701,notification());
        listening=true;
        listenAgain(300);
        return START_STICKY;
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"JARVIS — голосовой режим",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Показывает, когда JARVIS использует микрофон");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification notification(){
        Intent stop=new Intent(this,JarvisService.class).setAction("STOP");
        PendingIntent pi=PendingIntent.getService(this,702,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS активен")
            .setContentText("Ожидаю команду • микрофон включён")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"Выключить",pi).build();
    }

    private void listenAgain(long delay){
        if(!listening||restarting)return;
        restarting=true;
        handler.postDelayed(()->{
            restarting=false;
            if(!listening)return;
            try{
                if(!SpeechRecognizer.isRecognitionAvailable(this)){ say("Распознавание речи недоступно на этом телефоне"); stopSelf(); return; }
                if(recognizer!=null){ try{recognizer.destroy();}catch(Exception ignored){} }
                recognizer=SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new RecognitionListener(){
                    public void onReadyForSpeech(Bundle b){}
                    public void onBeginningOfSpeech(){}
                    public void onRmsChanged(float x){}
                    public void onBufferReceived(byte[] b){}
                    public void onEndOfSpeech(){}
                    public void onError(int e){ listenAgain(e==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?1800:900); }
                    public void onResults(Bundle b){
                        ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if(a!=null&&!a.isEmpty()) handlePhrase(a.get(0));
                        listenAgain(450);
                    }
                    public void onPartialResults(Bundle b){}
                    public void onEvent(int t,Bundle b){}
                });
                Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ru-RU");
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
                recognizer.startListening(i);
            }catch(Exception e){ listenAgain(1500); }
        },delay);
    }

    private String clean(String s){
        String x=Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return x.replaceAll("[^a-zа-яё0-9 ]"," ").replaceAll("\\s+"," ").trim();
    }

    private void handlePhrase(String phrase){
        String p=clean(phrase);
        int at=p.indexOf("джарвис"); int len=7;
        if(at<0){at=p.indexOf("jarvis");len=6;}
        if(at<0)return;
        String cmd=p.substring(at+len).trim().replaceFirst("^(пожалуйста |эй |слушай )","");
        if(cmd.isEmpty())return;
        if(isHome(cmd)){ launchHome(); return; }
        if(cmd.matches("^(назад|вернись назад|предыдущий экран)$")){ say("Команда назад пока недоступна из фонового режима"); return; }
        if(cmd.startsWith("позвони ")||cmd.startsWith("набери ")||cmd.startsWith("вызови ")||cmd.startsWith("позвонить ")){
            String name=cmd.replaceFirst("^(позвони|набери|вызови|позвонить) ","").trim(); callContact(name); return;
        }
        if(cmd.startsWith("напиши ")||cmd.startsWith("отправь сообщение ")||cmd.startsWith("смс ")){
            composeMessage(cmd); return;
        }
        if(cmd.contains("какую нибудь игру")||cmd.equals("запусти игру")||cmd.equals("открой игру")){
            say("Назови игру, например: открой Minecraft или запусти Roblox"); return;
        }
        String app=extractAppName(cmd);
        if(!app.isEmpty()){ launchApp(app); return; }
        if(cmd.contains("настройки")){ openIntent(new Intent(android.provider.Settings.ACTION_SETTINGS)); return; }
        if(cmd.contains("камера")){ launchApp("камера"); return; }
        say("Команду пока не поняла. Скажи: открой YouTube, позвони папе или напиши маме я скоро буду.");
    }

    private boolean isHome(String c){ return c.equals("домой")||c.equals("на главный экран")||c.equals("сверни приложение")||c.equals("вернись на главный экран"); }
    private void launchHome(){
        Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        openIntent(i);
    }

    private String extractAppName(String c){
        String[] prefixes={"открой приложение ","открой игру ","открой ","запусти приложение ","запусти игру ","запусти ","включи ","играй в ","поиграй в ","хочу поиграть в ","покажи ","перейди в "};
        for(String prefix:prefixes) if(c.startsWith(prefix)) return c.substring(prefix.length()).trim();
        return "";
    }

    private void launchApp(String requested){
        String target=clean(requested);
        Map<String,String> aliases=new LinkedHashMap<>();
        aliases.put("ютуб","youtube"); aliases.put("ютубчик","youtube"); aliases.put("гугл хром","chrome"); aliases.put("хром","chrome");
        aliases.put("браузер","browser"); aliases.put("телефон","phone"); aliases.put("сообщения","messages");
        aliases.put("телеграм","telegram"); aliases.put("телеграмм","telegram"); aliases.put("ватсап","whatsapp"); aliases.put("вотсап","whatsapp");
        aliases.put("роблокс","roblox"); aliases.put("майнкрафт","minecraft"); aliases.put("бравл старс","brawl stars");
        aliases.put("стендофф","standoff"); aliases.put("стендофф 2","standoff 2"); aliases.put("геншин","genshin");
        if(aliases.containsKey(target)) target=aliases.get(target);
        Intent query=new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(query,0);
        ResolveInfo best=null; int bestScore=0;
        for(ResolveInfo ri:apps){
            String label=clean(String.valueOf(ri.loadLabel(getPackageManager())));
            int score=0;
            if(label.equals(target))score=100;
            else if(label.contains(target)||target.contains(label))score=70;
            else {
                String[] words=target.split(" ");
                for(String word:words) if(word.length()>2 && label.contains(word))score+=20;
            }
            if(score>bestScore){bestScore=score;best=ri;}
        }
        if(best!=null&&bestScore>=20){
            Intent launch=getPackageManager().getLaunchIntentForPackage(best.activityInfo.packageName);
            if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);openIntent(launch);return;}
        }
        say("Не нашла приложение «"+requested+"». Проверь название или скажи точнее.");
    }

    private void callContact(String spokenName){
        Contact c=findContact(spokenName);
        if(c==null){say("Не нашла контакт «"+spokenName+"». Проверь, что он сохранён в телефонной книге.");return;}
        Intent i=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",c.phone,null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        say("Открываю набор номера: "+c.name);
        openIntent(i);
    }

    private void composeMessage(String command){
        String rest=command.replaceFirst("^(отправь сообщение|напиши|смс) ","").replaceFirst("^контакту ","").trim();
        Contact match=null; String body="";
        if(checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
            Cursor cur=null;
            try{
                cur=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null,null,null);
                int best=0;
                if(cur!=null)while(cur.moveToNext()){
                    String name=cur.getString(0), number=cur.getString(1);
                    String n=clean(name);
                    String prefix=contactPrefix(rest,n);
                    if(!prefix.isEmpty() && prefix.length()>best){
                        best=prefix.length(); match=new Contact(name,number); body=rest.substring(best).trim();
                    }
                }
            }catch(Exception ignored){}finally{if(cur!=null)cur.close();}
        } else { say("Разреши JARVIS доступ к контактам в настройках приложения."); return; }
        if(match==null){say("Не нашла получателя. Скажи, например: напиши папе я скоро буду.");return;}
        if(body.isEmpty()){say("Что написать "+match.name+"? Повтори команду вместе с текстом сообщения.");return;}
        Intent i=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",match.phone,null));
        i.putExtra("sms_body",body); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        say("Подготовила сообщение для "+match.name+". Проверь текст и нажми отправить.");
        openIntent(i);
    }


    private boolean contactNameMatches(String spoken,String saved){
        if(spoken.equals(saved)||saved.contains(spoken)||spoken.contains(saved))return true;
        String base=spoken;
        if(spoken.equals("папе")||spoken.equals("папу"))base="папа";
        if(spoken.equals("маме")||spoken.equals("маму"))base="мама";
        if(spoken.equals("бабушке"))base="бабушка";
        if(spoken.equals("дедушке"))base="дедушка";
        if(spoken.equals("брату"))base="брат";
        if(spoken.equals("сестре"))base="сестра";
        return saved.equals(base)||saved.contains(base)||base.contains(saved)
            || (base.equals("папа")&&saved.equals("папуля"))
            || (base.equals("мама")&&saved.equals("мамуля"));
    }

    private String contactPrefix(String rest,String saved){
        if(rest.startsWith(saved))return saved;
        String[] aliases={"папе","папу","маме","маму","бабушке","дедушке","брату","сестре","папуля","мамуля"};
        for(String a:aliases) if(rest.startsWith(a) && contactNameMatches(a,saved))return a;
        return "";
    }
\n    private Contact findContact(String spokenName){
        if(checkSelfPermission(android.Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){say("Разреши доступ к контактам в настройках приложения.");return null;}
        Cursor cur=null; Contact found=null; int best=0;
        try{
            cur=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},
                null,null,null);
            String wanted=clean(spokenName);
            if(cur!=null)while(cur.moveToNext()){
                String name=cur.getString(0), number=cur.getString(1), n=clean(name);
                if(contactNameMatches(wanted,n)){
                    if(n.length()>best){best=n.length();found=new Contact(name,number);}
                }
            }
        }catch(Exception ignored){}finally{if(cur!=null)cur.close();}
        return found;
    }

    private void openIntent(Intent i){
        try{if(i.resolveActivity(getPackageManager())!=null)startActivity(i);else say("На телефоне нет приложения для этой команды.");}
        catch(Exception e){say("Не получилось выполнить команду. Проверь разрешения телефона.");}
    }
    private void say(String s){ handler.post(()->Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show()); }

    private static class Contact{String name,phone;Contact(String n,String p){name=n;phone=p;}}

    @Override public void onDestroy(){
        listening=false;if(handler!=null)handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}}
        stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
