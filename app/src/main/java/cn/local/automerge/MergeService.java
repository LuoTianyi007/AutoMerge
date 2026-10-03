package cn.local.automerge;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import com.arthenica.ffmpegkit.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class MergeService extends Service {
    static volatile boolean running=false,cancelled=false;
    static volatile String status="";
    static volatile long sessionId=-1;
    static final ArrayList<String> log=new ArrayList<>();
    PowerManager.WakeLock wake;
    static synchronized String logText(){return String.join("\n",log);}
    static synchronized void add(String s){log.add(s);if(log.size()>180)log.remove(0);}
    static void requestCancel(){cancelled=true;long id=sessionId;if(id>=0)FFmpegKit.cancel(id);}
    public IBinder onBind(Intent i){return null;}
    Notification notification(String message,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"merging").setSmallIcon(cn.local.automerge.R.drawable.icon).setContentTitle("自动影音合成").setContentText(message).setContentIntent(pi).setOngoing(ongoing).setOnlyAlertOnce(true).build();
    }
    void state(String value){status=value;getSystemService(NotificationManager.class).notify(1,notification(value,true));}
    public int onStartCommand(Intent i,int flags,int id){
        if(running)return START_NOT_STICKY;
        running=true;cancelled=false;sessionId=-1;synchronized(MergeService.class){log.clear();}
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("merging","合成进度",NotificationManager.IMPORTANCE_LOW));
        status="正在扫描文件夹…";
        try{
            int type=Build.VERSION.SDK_INT>=35?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if(Build.VERSION.SDK_INT>=29)startForeground(1,notification(status,true),type);else startForeground(1,notification(status,true));
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AutoMerge:batch");wake.acquire(6*60*60*1000L);
            new Thread(this::runBatch,"MergeWorker").start();
        }catch(Exception e){status="不能启动合成："+e.getMessage();add(status);running=false;stopSelf();}
        return START_NOT_STICKY;
    }
    void runBatch(){
        int ok=0,skipped=0,failed=0,deleted=0,deleteFailed=0;
        try{
            state("正在检查合成引擎…");
            try {
                add("合成引擎：FFmpegKit "+FFmpegKitConfig.getVersion()+" / FFmpeg "+FFmpegKitConfig.getFFmpegVersion());
            } catch(Throwable engineError) {
                status="合成引擎初始化失败，请查看下方详细原因。";
                add(ErrorReport.describe(engineError));
                return;
            }
            state("正在扫描文件夹…");
            SharedPreferences prefs=getSharedPreferences("settings",0);
            final boolean deleteSources=prefs.getBoolean("deleteSources",false);
            add(deleteSources?"成功后删除原文件：开启（输出校验通过后执行）":"成功后删除原文件：关闭");
            Uri vroot=Tree.root(Uri.parse(prefs.getString("video",""))),aroot=Tree.root(Uri.parse(prefs.getString("audio",""))),oroot=Tree.root(Uri.parse(prefs.getString("output","")));
            List<Pairing.Item> videos=new ArrayList<>(),audios=new ArrayList<>();
            Tree.scan(this,vroot,"",true,videos,new HashSet<>(),0);Tree.scan(this,aroot,"",false,audios,new HashSet<>(),0);
            Pairing.Result result=Pairing.match(videos,audios);for(String warning:result.warnings)add(warning);
            Set<Pairing.Pair> verified=new HashSet<>();
            Set<String> outputIds=new HashSet<>();
            add("发现视频 "+videos.size()+" 个，音频 "+audios.size()+" 个，配对 "+result.pairs.size()+" 组。");
            int n=0;
            for(Pairing.Pair pair:result.pairs){
                if(cancelled)break;n++;final int index=n;final int total=result.pairs.size();
                Uri temp=null;
                try{
                    Uri dir=Tree.mkdirs(this,oroot,pair.video.relative);
                    String finalName=pair.video.stem()+"__merged.mkv";
                    if(Tree.find(this,dir,finalName)!=null){skipped++;add("已存在，跳过："+finalName);continue;}
                    if(cancelled)break;
                    temp=DocumentsContract.createDocument(getContentResolver(),dir,"video/x-matroska",pair.video.stem()+".merging-"+UUID.randomUUID()+".mkv");
                    if(temp==null)throw new Exception("不能创建输出，请检查文件夹授权和存储空间");
                    final String label=pair.video.name;
                    state("合成 "+index+" / "+total+"："+label);
                    String vin=FFmpegKitConfig.getSafParameterForRead(this,Uri.parse(pair.video.uri));
                    String ain=FFmpegKitConfig.getSafParameterForRead(this,Uri.parse(pair.audio.uri));
                    String out=FFmpegKitConfig.getSafParameterForWrite(this,temp);
                    CountDownLatch done=new CountDownLatch(1);
                    final FFmpegSession[] completed=new FFmpegSession[1];
                    FFmpegSession session=FFmpegKit.executeWithArgumentsAsync(new String[]{"-hide_banner","-nostdin","-y","-i",vin,"-i",ain,"-map","0:v:0","-map","1:a:0","-c:v","copy","-c:a","copy","-f","matroska",out},s->{completed[0]=s;done.countDown();},null,null);
                    sessionId=session.getSessionId();if(cancelled)FFmpegKit.cancel(sessionId);
                    done.await();sessionId=-1;
                    if(cancelled){deletePartial(temp);temp=null;break;}
                    FFmpegSession finished=completed[0];
                    if(finished==null||!ReturnCode.isSuccess(finished.getReturnCode())) {
                        String detail=finished==null?"合成未完成":finished.getAllLogsAsString();
                        if(detail==null)detail="无法读取输入文件或写入输出";
                        if(detail.length()>1200)detail=detail.substring(detail.length()-1200);
                        throw new Exception(detail);
                    }
                    if(Tree.find(this,dir,finalName)!=null)throw new Exception("目标文件已出现，未覆盖；请重新运行");
                    Uri renamed=DocumentsContract.renameDocument(getContentResolver(),temp,finalName);
                    if(renamed==null)throw new Exception("文件夹不支持重命名，请选择手机本地文件夹");
                    outputIds.add(documentKey(renamed));
                    temp=null;ok++;add("完成："+(pair.video.relative.isEmpty()?"":pair.video.relative+"/")+finalName);
                    if(deleteSources&&!cancelled) {
                        state("检查输出 "+index+" / "+total+"："+label);
                        try {
                            if(verifyOutput(renamed))verified.add(pair);
                            else add("保留原文件：输出音视频流校验未通过，"+finalName);
                        } catch(Throwable e) {add("保留原文件：输出校验失败，"+finalName+"\n"+ErrorReport.describe(e));}
                    }
                }catch(Exception e){failed++;add("失败："+pair.video.name+"\n"+e.getMessage());}
                finally{if(temp!=null)deletePartial(temp);}
            }
            List<Pairing.Item> cleanup=CleanupPlan.create(deleteSources,cancelled,result.pairs,verified);
            if(!cleanup.isEmpty())state("正在删除已成功合成的原文件…");
            for(Pairing.Item source:cleanup) {
                if(cancelled)break;
                try {
                    Uri uri=Uri.parse(source.uri);
                    if(outputIds.contains(documentKey(uri)))throw new Exception("原文件与输出指向相同文档，已保留");
                    if(!deleteOriginal(uri))throw new Exception("文件夹未允许删除，请重新授权或手动删除");
                    deleted++;add("已删除原文件："+(source.relative.isEmpty()?"":source.relative+"/")+source.name);
                }catch(Exception e){deleteFailed++;add("原文件删除失败："+source.name+"\n"+e.getMessage());}
            }
            if(deleteSources&&cancelled)add("已停止；剩余原文件保留。");
            status=(cancelled?"已停止":"本次完成")+" · 成功 "+ok+" · 已有 "+skipped+" · 失败 "+failed+" · 未配对 "+result.warnings.size();
            if(deleteSources)status+=" · 原文件已删 "+deleted+" · 删除失败 "+deleteFailed;
            if(result.pairs.isEmpty())add("没有可合成的配对。请确认视频和音频文件名除扩展名外完全相同。");
        }catch(InterruptedException e){status="已停止";}
        catch(Throwable e){status="合成中断："+e.getClass().getSimpleName()+"："+e.getMessage();add(ErrorReport.describe(e));}
        finally{
            sessionId=-1;running=false;
            if(wake!=null&&wake.isHeld())wake.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            getSystemService(NotificationManager.class).notify(2,notification(status,false));
            getSharedPreferences("settings",0).edit().putString("lastStatus",status).putString("lastLog",logText()).apply();
            stopSelf();
        }
    }
    boolean verifyOutput(Uri output) {
        MediaInformationSession check=FFprobeKit.getMediaInformation(FFmpegKitConfig.getSafParameterForRead(this,output));
        MediaInformation info=check.getMediaInformation();
        if(!ReturnCode.isSuccess(check.getReturnCode())||info==null||info.getStreams()==null)return false;
        boolean video=false,audio=false;
        for(StreamInformation stream:info.getStreams()) {
            if("video".equals(stream.getType()))video=true;
            if("audio".equals(stream.getType()))audio=true;
        }
        return video&&audio;
    }
    static String documentKey(Uri uri){return uri.getAuthority()+"/"+DocumentsContract.getDocumentId(uri);}
    boolean deleteOriginal(Uri uri) throws Exception {
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS},null,null,null)) {
            if(cursor==null||!cursor.moveToFirst())throw new Exception("无法读取原文件，已保留");
            if(DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(0)))throw new Exception("不会删除文件夹");
            if((cursor.getInt(1)&DocumentsContract.Document.FLAG_SUPPORTS_DELETE)==0)return false;
        }
        if(cancelled)return false;
        return DocumentsContract.deleteDocument(getContentResolver(),uri);
    }
    void deletePartial(Uri uri){try{DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception e){add("未能清理临时文件，请在输出文件夹删除 .merging 文件："+e.getMessage());}}
    public void onTimeout(int startId,int fgsType){requestCancel();status="达到系统后台处理时限，已停止；可再次开始继续。";stopSelf();}
}
