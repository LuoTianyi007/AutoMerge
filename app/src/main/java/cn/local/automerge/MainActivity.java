package cn.local.automerge;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String[] KEYS={"video","audio","output"};
    final int ink=Color.rgb(28,48,44), teal=Color.rgb(20,107,92), muted=Color.rgb(96,111,104);
    final Handler handler=new Handler(Looper.getMainLooper());
    final List<Button> picks=new ArrayList<>();
    TextView status,logs; Button start,cancel; ProgressBar progress; Switch deleteSources;
    SharedPreferences prefs;
    final Runnable update=new Runnable(){public void run(){refresh();handler.postDelayed(this,700);}};
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:teal);b.setBackground(bg(primary?teal:Color.rgb(227,239,230),14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(9),0,dp(5));b.setLayoutParams(lp);return b;}
    public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("settings",0);
        getWindow().setStatusBarColor(Color.rgb(246,247,239));getWindow().setNavigationBarColor(Color.rgb(246,247,239));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(246,247,239));
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(22),dp(22),dp(22),dp(28));scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});scroll.requestApplyInsets();
        if(!MergeService.running&&MergeService.status.isEmpty()){MergeService.status=prefs.getString("lastStatus","");String saved=prefs.getString("lastLog","");if(!saved.isEmpty())MergeService.add(saved);}
        TextView badge=text("LOCAL  /  LOSSLESS",12,teal);badge.setLetterSpacing(.12f);body.addView(badge);
        TextView title=text("自动影音合成",30,ink);title.setTypeface(null,Typeface.BOLD);body.addView(title);
        body.addView(text("同名自动配对，点一次批量完成。",16,muted));
        String[] labels={"01   视频文件夹","02   音频文件夹","03   输出文件夹"};
        for(int i=0;i<3;i++) {
            final int index=i;
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(bg(Color.WHITE,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(12),0,0);body.addView(card,cp);
            TextView label=text(labels[i],14,muted);card.addView(label);
            Button pick=button("选择文件夹",false);pick.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);pick.setPadding(dp(14),0,dp(14),0);pick.setTextSize(13);pick.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(66)));pick.setOnClickListener(v->choose(index));card.addView(pick);picks.add(pick);
        }
        body.addView(text("输出 MKV · 视频和音频均无损复制\n使用外部音频替换原声；扫描所有子文件夹。",13,muted));
        deleteSources=new Switch(this);deleteSources.setText("合成成功后删除原视频和音频");deleteSources.setTextColor(ink);deleteSources.setTextSize(14);deleteSources.setPadding(0,dp(12),0,dp(8));
        deleteSources.setChecked(prefs.getBoolean("deleteSources",false));
        deleteSources.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("deleteSources",checked).apply());
        body.addView(deleteSources);
        body.addView(text("默认关闭。开启后，在本批合成结束时删除校验通过的原文件。删除无法撤销；失败或已有结果不会删除。",12,muted));
        start=button("开始自动合成",true);body.addView(start);start.setOnClickListener(v->begin());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        status=text("请选择三个文件夹。设置会自动保存。",15,ink);body.addView(status);
        cancel=button("停止本次合成",false);body.addView(cancel);cancel.setOnClickListener(v->{MergeService.requestCancel();status.setText("正在停止，清理未完成的输出…");});
        logs=text("",12,muted);logs.setTextIsSelectable(true);body.addView(logs);
        TextView help=text("使用说明与开源信息",13,teal);help.setPadding(0,dp(20),0,dp(8));body.addView(help);help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("使用说明").setMessage("首次选择视频、音频和输出文件夹，以后点开始即可。\n\n例：相同名称的 .mov 和 .wav 会配对。优先匹配相同子目录；否则匹配唯一的同名音频。音频重名时跳过，避免误配。\n\n输出保留日期子目录，命名为 文件名__merged.mkv。已存在的结果会跳过。删除原文件开关默认关闭；开启后，仅在新输出通过音视频流检查、本批合成结束时删除对应原文件。失败、跳过或取消的任务保留原文件。删除失败会在日志提示，删除无法撤销。音画必须来自同一录制起点，本应用不自动校正时间偏移。\n\n请选择手机本地文件夹。Android 的文件选择器可能不允许直接授权 Download 根目录，可在其中新建“合成输出”文件夹。\n\nAndroid 8.0 及以上。完全离线，无广告。\n\nFFmpegKit 精简库 6.1.4：github.com/JamaisMagic/ffmpeg-kit-16KB\nFFmpeg：ffmpeg.org\n应用 GPL-3.0-or-later；FFmpeg 精简库 LGPL-3.0-or-later。对应源码、版本和构建记录见 GitHub 发布附件。").setPositiveButton("知道了",null).show());
        updateFolders();
    }
    void choose(int index){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent,100+index);
    }
    protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request<100||request>102||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{getContentResolver().takePersistableUriPermission(uri,flags);prefs.edit().putString(KEYS[request-100],uri.toString()).apply();updateFolders();}
        catch(Exception e){new AlertDialog.Builder(this).setMessage("不能保存授权，请选择手机本地文件夹。\n"+e.getMessage()).setPositiveButton("确定",null).show();}
    }
    void updateFolders(){for(int i=0;i<3;i++){String value=prefs.getString(KEYS[i],"");picks.get(i).setText(value.isEmpty()?"选择文件夹":Tree.display(this,Uri.parse(value)));}}
    void begin(){
        for(String key:KEYS)if(prefs.getString(key,"").isEmpty()){Toast.makeText(this,"请先选择三个文件夹",Toast.LENGTH_LONG).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},77);
        try{startForegroundService(new Intent(this,MergeService.class));}catch(Exception e){Toast.makeText(this,"启动失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void refresh(){
        boolean running=MergeService.running;start.setEnabled(!running);for(Button pick:picks)pick.setEnabled(!running);deleteSources.setEnabled(!running);
        cancel.setVisibility(running?View.VISIBLE:View.GONE);cancel.setEnabled(!MergeService.cancelled);
        progress.setVisibility(running?View.VISIBLE:View.GONE);
        String state=MergeService.status;if(!state.isEmpty())status.setText(state);
        logs.setText(MergeService.logText());
    }
    protected void onResume(){super.onResume();handler.post(update);}
    protected void onPause(){handler.removeCallbacks(update);super.onPause();}
}
