package com.aethershell.launcher;

import android.app.*;
import android.app.WallpaperManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import java.io.*;
import java.text.*;
import java.util.*;

/** Offline launcher + static wallpaper studio. No background services or tracking. */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGE=118;
    private static final String PREF="aethershell_settings";
    private static final int BG=0xff0b101b, CARD=0xff171f2e, WHITE=0xffeef4ff, MUTED=0xff9faec4;
    private static final int[] ACCENTS={0xff89c8ff,0xffc9a3ff,0xff7ce1cd,0xffffb0bb,0xffffd09d,0xffcbd7ec};
    private static final String[] ACCENT_NAMES={"Arctic","Lavender","Mint","Rose","Amber","Slate"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<AppEntry> apps=new ArrayList<>();
    private final ArrayList<String> favorites=new ArrayList<>();
    private final LruCache<String,Drawable.ConstantState> iconCache=new LruCache<>(120);
    private android.content.SharedPreferences prefs;
    private FrameLayout root;
    private TextView homeClock, homeDate;
    private int page=0, wallpaper=0, accent=0, columns=4, iconSize=52;
    private boolean amoled=false, labels=true, clock=true, monoIcons=false, loaded=false, destroyed=false;
    private Uri photoUri;
    private final Runnable tick=new Runnable(){@Override public void run(){updateClock();handler.postDelayed(this,30000L);}};
    private static final class AppEntry {String pkg,cls,label;AppEntry(String p,String c,String l){pkg=p;cls=c;label=l;}String id(){return pkg+"/"+cls;}}

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences(PREF,MODE_PRIVATE);readSettings();showPage(0);loadApps();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);showPage(0);}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(tick);tick.run();}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacks(tick);iconCache.evictAll();super.onDestroy();}
    private void readSettings(){accent=prefs.getInt("accent",0);wallpaper=prefs.getInt("wallpaper",0);columns=prefs.getInt("columns",4);iconSize=prefs.getInt("iconSize",52);
        amoled=prefs.getBoolean("amoled",false);labels=prefs.getBoolean("labels",true);clock=prefs.getBoolean("clock",true);monoIcons=prefs.getBoolean("mono",false);
        favorites.clear();String raw=prefs.getString("favorites","");if(!raw.isEmpty())favorites.addAll(Arrays.asList(raw.split("\\|")));
        String u=prefs.getString("photo",null);if(u!=null)try{photoUri=Uri.parse(u);}catch(Exception ignored){}
    }
    private void save(){prefs.edit().putInt("accent",accent).putInt("wallpaper",wallpaper).putInt("columns",columns).putInt("iconSize",iconSize)
        .putBoolean("amoled",amoled).putBoolean("labels",labels).putBoolean("clock",clock).putBoolean("mono",monoIcons)
        .putString("favorites",android.text.TextUtils.join("|",favorites)).putString("photo",photoUri==null?null:photoUri.toString()).apply();}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int accent(){return ACCENTS[Math.floorMod(accent,ACCENTS.length)];}
    private GradientDrawable shape(int color,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));return d;}
    private GradientDrawable border(int color,int radius,int stroke){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void pad(View v,int h,int vpad){v.setPadding(dp(h),dp(vpad),dp(h),dp(vpad));}
    private void gap(LinearLayout l,int h){l.addView(new View(this),lp(1,h));}
    private TextView pill(String title,Runnable action){TextView t=text(title,13,WHITE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),dp(11),dp(14),dp(11));t.setBackground(shape(CARD,18));t.setOnClickListener(v->action.run());return t;}
    private TextView primary(String title,Runnable action){TextView t=pill(title,action);t.setTextColor(0xff051020);t.setBackground(shape(accent(),18));return t;}
    private void showPage(int dest){page=dest;homeClock=null;homeDate=null;
        root=new FrameLayout(this);root.setBackgroundColor(BG);
        // Android 15/16 edge-to-edge: keep navigation, clock, and controls clear of system bars.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=safe.left;top=safe.top;right=safe.right;bottom=safe.bottom;
            }else{
                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
            }
            if(view.getPaddingLeft()!=left||view.getPaddingTop()!=top||view.getPaddingRight()!=right||view.getPaddingBottom()!=bottom)
                view.setPadding(left,top,right,bottom);
            return insets;
        });
        setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=vertical();pad(body,18,14);scroll.addView(body);
        FrameLayout.LayoutParams content=new FrameLayout.LayoutParams(-1,-1);content.bottomMargin=dp(74);root.addView(scroll,content);
        header(body);if(page==0)buildHome(body);else if(page==1)buildWalls(body);else buildStyle(body);
        navBar(); updateClock();
    }
    private void header(LinearLayout body){LinearLayout bar=horizontal();
        TextView logo=text("✦",28,accent(),true);bar.addView(logo,lp(40,44));
        LinearLayout titles=vertical();titles.addView(text("AETHERSHELL",17,WHITE,true));titles.addView(text("Make your screen yours",11,MUTED,false));bar.addView(titles,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView info=pill("⋮",this::showAbout);info.setTextSize(22);bar.addView(info,lp(50,42));body.addView(bar);gap(body,22);
    }
    private void navBar(){LinearLayout bar=horizontal();bar.setGravity(Gravity.CENTER);bar.setBackground(shape(0xff111827,23));
        String[] items={"⌂  Home","◈  Wallpapers","⚙  Style"};for(int i=0;i<3;i++){final int j=i;
            TextView button=text(items[i],12,i==page?0xff07111d:MUTED,true);button.setGravity(Gravity.CENTER);button.setBackground(shape(i==page?accent():0xff111827,18));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);bar.addView(button,p);button.setOnClickListener(v->showPage(j));}
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(56),Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),dp(10));root.addView(bar,p);
    }
    private void buildHome(LinearLayout body){body.addView(text("Your space",27,WHITE,true));gap(body,4);
        body.addView(text("Lightweight. Personal. Yours.",13,MUTED,false));gap(body,16);
        FrameLayout preview=new FrameLayout(this);preview.setBackground(shape(CARD,26));preview.setClipToOutline(true);
        WallpaperArt.Preview art=new WallpaperArt.Preview(this,wallpaper,amoled);preview.addView(art,new FrameLayout.LayoutParams(-1,-1));
        if(photoUri!=null){ImageView photo=imagePreview();if(photo!=null)preview.addView(photo,new FrameLayout.LayoutParams(-1,-1));}
        LinearLayout overlay=vertical();pad(overlay,20,22);
        TextView chip=text("YOUR HOME",10,0xffe0ebfc,true);chip.setLetterSpacing(.22f);overlay.addView(chip);gap(overlay,24);
        homeClock=text("08:30",44,0xffffffff,true);homeClock.setShadowLayer(3,0,2,0x66000000);overlay.addView(homeClock);
        homeDate=text("Friday, October 30",13,0xffe8ecff,false);homeDate.setShadowLayer(3,0,2,0x66000000);overlay.addView(homeDate);homeClock.setVisibility(clock?View.VISIBLE:View.GONE);homeDate.setVisibility(clock?View.VISIBLE:View.GONE);
        gap(overlay,38);
        TextView caption=text("PINNED APPS",10,0xffe5ebff,true);caption.setLetterSpacing(.18f);overlay.addView(caption);gap(overlay,11);
        GridLayout pins=new GridLayout(this);pins.setColumnCount(columns);populatePinned(pins);overlay.addView(pins);
        gap(overlay,22);TextView search=pill("⌕   Search all apps",this::appDrawer);search.setBackground(border(0x99202a40,20,0x667d9cb8));overlay.addView(search,lp(-1,48));
        preview.addView(overlay,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        // Height is fixed only for the preview card, not the entire launcher UI.
        body.addView(preview,lp(-1,475));gap(body,16);
        LinearLayout actions=horizontal();actions.addView(primary("All apps  ↗",this::appDrawer),new LinearLayout.LayoutParams(0,dp(46),1));
        View space=new View(this);actions.addView(space,lp(10,1));
        actions.addView(pill("Set as launcher",this::requestHome),new LinearLayout.LayoutParams(0,dp(46),1));body.addView(actions);
        gap(body,19);body.addView(text("QUICK ACCESS",11,MUTED,true));gap(body,11);
        LinearLayout quick=horizontal();quick.addView(pill("Display",()->openSettings(Settings.ACTION_DISPLAY_SETTINGS)),new LinearLayout.LayoutParams(0,dp(45),1));quick.addView(new View(this),lp(8,1));
        quick.addView(pill("Wallpaper",()->showPage(1)),new LinearLayout.LayoutParams(0,dp(45),1));quick.addView(new View(this),lp(8,1));
        quick.addView(pill("Settings",()->openSettings(Settings.ACTION_SETTINGS)),new LinearLayout.LayoutParams(0,dp(45),1));body.addView(quick);
    }
    private ImageView imagePreview(){
        if(photoUri==null)return null;
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xff17202e);
        final Uri imageUri=photoUri;
        new Thread(()->{
            Bitmap decoded=null;
            try{
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                try(InputStream stream=getContentResolver().openInputStream(imageUri)){
                    BitmapFactory.decodeStream(stream,null,bounds);
                }
                if(bounds.outWidth>0 && bounds.outHeight>0){
                    int sample=1;
                    while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>1400)sample*=2;
                    BitmapFactory.Options options=new BitmapFactory.Options();
                    options.inSampleSize=sample;
                    try(InputStream stream=getContentResolver().openInputStream(imageUri)){
                        decoded=BitmapFactory.decodeStream(stream,null,options);
                    }
                }
            }catch(Exception ignored){}
            final Bitmap result=decoded;
            handler.post(()->{
                if(destroyed || !imageUri.equals(photoUri) || image.getParent()==null){
                    if(result!=null)result.recycle();
                    return;
                }
                image.setImageBitmap(result);
            });
        },"AetherPhotoPreview").start();
        return image;
    }
    private void populatePinned(GridLayout pins){pins.removeAllViews();int max=Math.min(favorites.size(),columns*2);
        if(max==0){TextView empty=text("No pinned apps yet. Open All apps and long-press any icon to pin it here.",12,0xfff3f4ff,false);pad(empty,5,12);pins.addView(empty);return;}
        for(int i=0;i<max;i++){AppEntry a=findApp(favorites.get(i));if(a==null)continue;
            LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);ImageView icon=new ImageView(this);icon.setImageDrawable(loadIcon(a));
            cell.addView(icon,lp(iconSize,iconSize));if(labels){TextView label=text(a.label,10,WHITE,false);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);cell.addView(label,lp(Math.max(56,280/columns),26));}
            GridLayout.LayoutParams param=new GridLayout.LayoutParams();param.width=0;param.height=dp(92);param.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);pins.addView(cell,param);
            cell.setOnClickListener(v->launch(a));cell.setOnLongClickListener(v->{showAppActions(a);return true;});
        }
    }
    private void buildWalls(LinearLayout body){body.addView(text("Wallpaper studio",27,WHITE,true));gap(body,5);
        body.addView(text("12 original offline compositions · no live services",12,MUTED,false));gap(body,17);
        FrameLayout hero=new FrameLayout(this);hero.setClipToOutline(true);hero.setBackground(shape(CARD,24));
        WallpaperArt.Preview art=new WallpaperArt.Preview(this,wallpaper,amoled);hero.addView(art,new FrameLayout.LayoutParams(-1,-1));
        if(photoUri!=null){ImageView image=imagePreview();if(image!=null)hero.addView(image,new FrameLayout.LayoutParams(-1,-1));}
        LinearLayout over=vertical();pad(over,17,16);over.addView(text(photoUri==null?WallpaperArt.NAMES[wallpaper]:"Your photo",20,WHITE,true));
        over.addView(text("Tap an artwork below to preview",11,WHITE,false));hero.addView(over);
        body.addView(hero,lp(-1,330));gap(body,12);
        LinearLayout buttons=horizontal();buttons.addView(primary("Apply wallpaper",this::applyDialog),new LinearLayout.LayoutParams(0,dp(50),1));buttons.addView(new View(this),lp(8,1));
        buttons.addView(pill("Save PNG",this::exportWallpaper),new LinearLayout.LayoutParams(0,dp(50),1));body.addView(buttons);gap(body,9);
        body.addView(pill("＋  Use a photo from your phone",this::choosePhoto),lp(-1,48));gap(body,9);
        if(photoUri!=null){body.addView(pill("✕  Return to original artwork",()->{clearPhoto();save();showPage(1);}),lp(-1,46));gap(body,9);}
        gap(body,13);
        body.addView(text("ORIGINAL COLLECTION",11,MUTED,true));gap(body,10);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(3);body.addView(grid);
        for(int i=0;i<WallpaperArt.NAMES.length;i++){final int choice=i;
            LinearLayout tile=vertical();tile.setPadding(dp(3),dp(3),dp(3),dp(9));
            FrameLayout thumb=new FrameLayout(this);thumb.setBackground(border(CARD,15,choice==wallpaper&&photoUri==null?accent():CARD));thumb.setClipToOutline(true);
            WallpaperArt.Preview image=new WallpaperArt.Preview(this,i,amoled);thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
            tile.addView(thumb,lp(-1,141));TextView name=text(WallpaperArt.NAMES[i],11,choice==wallpaper&&photoUri==null?accent():WHITE,true);name.setGravity(Gravity.CENTER);tile.addView(name,lp(-1,24));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(183);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);grid.addView(tile,p);
            tile.setOnClickListener(v->{clearPhoto();wallpaper=choice;save();showPage(1);});
        }
    }
    private void buildStyle(LinearLayout body){body.addView(text("Personalize",27,WHITE,true));gap(body,5);
        body.addView(text("Your preferences stay on this device.",12,MUTED,false));gap(body,21);
        section(body,"ACCENT COLOR");GridLayout palette=new GridLayout(this);palette.setColumnCount(3);body.addView(palette);
        for(int i=0;i<ACCENTS.length;i++){final int choice=i;TextView b=text((i==accent?"✓  ":"●  ")+ACCENT_NAMES[i],12,i==accent?BG:WHITE,true);b.setGravity(Gravity.CENTER);
            b.setBackground(border(i==accent?ACCENTS[i]:CARD,16,ACCENTS[i]));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(55);p.setMargins(dp(3),dp(4),dp(3),dp(4));p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);palette.addView(b,p);
            b.setOnClickListener(v->{accent=choice;save();showPage(2);});}
        gap(body,19);section(body,"HOME SCREEN");
        settingChoice(body,"App grid",columns+" columns",new String[]{"3 columns","4 columns","5 columns"},new int[]{3,4,5},columns,x->{columns=x;save();showPage(2);});
        settingChoice(body,"Icon size",iconSize+" dp",new String[]{"44 dp","52 dp","60 dp"},new int[]{44,52,60},iconSize,x->{iconSize=x;save();showPage(2);});
        settingSwitch(body,"Show app names","Display labels under pinned icons",labels,checked->{labels=checked;save();});
        settingSwitch(body,"Show clock & date","Simple text; only updates while open",clock,checked->{clock=checked;save();});
        settingSwitch(body,"Minimal icons","Show letter icons in app drawer",monoIcons,checked->{monoIcons=checked;save();});
        gap(body,15);section(body,"DISPLAY & PERFORMANCE");
        settingSwitch(body,"AMOLED wallpaper base","True black in generated artworks",amoled,checked->{amoled=checked;save();});
        settingRow(body,"System display options","Open HyperOS display settings",()->openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        settingRow(body,"System launcher preferences","Choose your default Home app",this::requestHome);
        gap(body,14);section(body,"DATA & PRIVACY");
        settingRow(body,"Reset AetherShell preferences","Does not change system wallpaper",()->new AlertDialog.Builder(this).setTitle("Reset preferences?")
            .setMessage("Pins, colors and app appearance will return to defaults. System wallpaper will not change.")
            .setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{clearPhoto();prefs.edit().clear().apply();readSettings();showPage(2);}).show());
        body.addView(text("No internet permission · no analytics · no background service · no accessibility service.",11,MUTED,false));
    }
    private void section(LinearLayout l,String title){TextView t=text(title,11,MUTED,true);t.setLetterSpacing(.12f);l.addView(t);gap(l,9);}
    private interface IntChoice {void take(int value);}
    private interface BoolChoice {void take(boolean enabled);}
    private void settingChoice(LinearLayout body,String title,String description,String[] labels,int[] values,int current,IntChoice update){
        settingRow(body,title,description,()->new AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(labels,findIndex(values,current),(dialog,which)->{update.take(values[which]);dialog.dismiss();}).setNegativeButton("Cancel",null).show());}
    private int findIndex(int[] v,int x){for(int i=0;i<v.length;i++)if(v[i]==x)return i;return 0;}
    private void settingSwitch(LinearLayout body,String title,String subtitle,boolean checked,BoolChoice update){
        LinearLayout row=horizontal();pad(row,13,12);row.setBackground(shape(CARD,16));LinearLayout words=vertical();words.addView(text(title,14,WHITE,true));words.addView(text(subtitle,11,MUTED,false));
        row.addView(words,new LinearLayout.LayoutParams(0,dp(50),1));Switch sw=new Switch(this);sw.setChecked(checked);sw.setOnCheckedChangeListener((b,on)->update.take(on));row.addView(sw);body.addView(row);gap(body,8);
    }
    private void settingRow(LinearLayout body,String title,String subtitle,Runnable action){
        LinearLayout row=horizontal();pad(row,13,12);row.setBackground(shape(CARD,16));LinearLayout words=vertical();words.addView(text(title,14,WHITE,true));words.addView(text(subtitle,11,MUTED,false));
        row.addView(words,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(text("›",24,accent(),true));row.setOnClickListener(v->action.run());body.addView(row);gap(body,8);
    }
    private void updateClock(){if(homeClock==null)return;Date now=new Date();homeClock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));
        homeDate.setText(new SimpleDateFormat("EEEE, MMMM d",Locale.getDefault()).format(now));}
    private void openSettings(String action){try{startActivity(new Intent(action));}catch(Exception e){toast("Settings are not available on this device");}}
    private void requestHome(){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){new AlertDialog.Builder(this).setTitle("Set default launcher")
        .setMessage("Open Settings → Apps → Default apps → Launcher and choose AetherShell.").setPositiveButton("OK",null).show();}}
    private void showAbout(){new AlertDialog.Builder(this).setTitle("AetherShell 0.1")
        .setMessage("A lightweight Android launcher and wallpaper studio.\n\nAetherShell can customize its own Home screen and set static wallpapers. Android does not allow a normal app to replace HyperOS notification shade, lock screen controls or system-wide icons.\n\nEverything runs offline.")
        .setPositiveButton("Done",null).show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    private void loadApps(){new Thread(()->{
        ArrayList<AppEntry> found=new ArrayList<>();PackageManager pm=getPackageManager();Intent intent=new Intent(Intent.ACTION_MAIN);intent.addCategory(Intent.CATEGORY_LAUNCHER);
        try{for(ResolveInfo res:pm.queryIntentActivities(intent,0)){
            if(res.activityInfo==null)continue;String p=res.activityInfo.packageName;String cls=res.activityInfo.name;
            CharSequence label=res.loadLabel(pm);found.add(new AppEntry(p,cls,label==null?p:label.toString()));
        }}catch(Exception ignored){}found.sort((a,b)->a.label.compareToIgnoreCase(b.label));
        handler.post(()->{if(destroyed)return;apps.clear();apps.addAll(found);loaded=true;
            // A deliberately empty favorites list must stay empty on the next launch.
            if(!prefs.contains("favorites")){
                for(String q:new String[]{"camera","phone","message","chrome"})for(AppEntry a:apps)if(a.pkg.toLowerCase(Locale.ROOT).contains(q)&&!favorites.contains(a.id())){favorites.add(a.id());break;}
                save();
            }
            if(page==0)showPage(0);
        });
    },"AetherCatalog").start();}
    private AppEntry findApp(String id){for(AppEntry a:apps)if(a.id().equals(id))return a;return null;}
    private Drawable loadIcon(AppEntry a){try{Drawable.ConstantState state=iconCache.get(a.id());
        if(state!=null)return state.newDrawable(getResources()).mutate();Drawable d=getPackageManager().getActivityIcon(new ComponentName(a.pkg,a.cls));
        if(d.getConstantState()!=null)iconCache.put(a.id(),d.getConstantState());return d;
    }catch(Exception e){return getDrawable(android.R.drawable.sym_def_app_icon);}}
    private void launch(AppEntry a){try{Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);i.setComponent(new ComponentName(a.pkg,a.cls));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(i);}
        catch(Exception e){toast("This app cannot be opened");}}
    private void showAppActions(AppEntry a){boolean pinned=favorites.contains(a.id());new AlertDialog.Builder(this).setTitle(a.label)
        .setItems(new String[]{pinned?"Remove from Home":"Pin to Home","App information"},(dialog,which)->{
            if(which==0){if(pinned)favorites.remove(a.id());else favorites.add(a.id());save();if(page==0)showPage(0);else appDrawer();}
            else{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.pkg)));}catch(Exception ignored){}}
        }).show();}
    private void appDrawer(){Dialog dialog=new Dialog(this);LinearLayout frame=vertical();frame.setBackground(shape(0xff101725,24));pad(frame,15,16);
        LinearLayout title=horizontal();title.addView(text("All apps",22,WHITE,true),new LinearLayout.LayoutParams(0,dp(44),1));TextView close=pill("✕",dialog::dismiss);title.addView(close);frame.addView(title);
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setTextSize(14);input.setHint("Search apps…");input.setPadding(dp(15),dp(9),dp(15),dp(9));input.setBackground(shape(CARD,18));frame.addView(input,lp(-1,49));gap(frame,10);
        GridView grid=new GridView(this);grid.setNumColumns(columns);grid.setVerticalSpacing(dp(14));grid.setHorizontalSpacing(dp(5));grid.setClipToPadding(false);
        ArrayList<AppEntry> filtered=new ArrayList<>(apps);AppAdapter adapter=new AppAdapter(filtered);grid.setAdapter(adapter);frame.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        grid.setOnItemClickListener((parent,view,pos,id)->{AppEntry a=filtered.get(pos);dialog.dismiss();launch(a);});
        grid.setOnItemLongClickListener((parent,view,pos,id)->{dialog.dismiss();showAppActions(filtered.get(pos));return true;});
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int start,int before,int count){filtered.clear();String needle=s.toString().toLowerCase(Locale.ROOT).trim();
            for(AppEntry a:apps)if(a.label.toLowerCase(Locale.ROOT).contains(needle)||a.pkg.contains(needle))filtered.add(a);adapter.notifyDataSetChanged();}public void afterTextChanged(Editable e){} });
        if(!loaded)frame.addView(text("Loading installed apps…",12,MUTED,false));
        dialog.setContentView(frame);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setDimAmount(.65f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }
    private final class AppAdapter extends BaseAdapter {
        private final ArrayList<AppEntry> rows;AppAdapter(ArrayList<AppEntry> rows){this.rows=rows;}
        public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,android.view.ViewGroup parent){LinearLayout cell;
            if(convert instanceof LinearLayout)cell=(LinearLayout)convert;else{
                cell=vertical();cell.setGravity(Gravity.CENTER);ImageView icon=new ImageView(MainActivity.this);icon.setTag("icon");cell.addView(icon,lp(iconSize,iconSize));
                TextView label=text("",11,WHITE,false);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);cell.addView(label,lp(-1,24));
            }
            AppEntry a=rows.get(pos);ImageView icon=(ImageView)cell.getChildAt(0);TextView label=(TextView)cell.getChildAt(1);
            if(monoIcons){icon.setImageDrawable(monogram(a.label));}else icon.setImageDrawable(loadIcon(a));
            label.setText(a.label);label.setVisibility(labels?View.VISIBLE:View.INVISIBLE);cell.setLayoutParams(new AbsListView.LayoutParams(-1,dp(94)));
            return cell;
        }
    }
    private Drawable monogram(String name){GradientDrawable d=shape(CARD,18); // drawn as a single glyph without decoding icons
        return new Drawable(){final Paint p=new Paint(3);public void draw(Canvas c){android.graphics.Rect r=getBounds();p.setColor(accent());c.drawRoundRect(r.left,r.top,r.right,r.bottom,dp(14),dp(14),p);
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setColor(BG);p.setTextSize((r.bottom-r.top)*.54f);p.setTextAlign(Paint.Align.CENTER);
            String letter=name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT);c.drawText(letter,r.exactCenterX(),r.exactCenterY()-(p.ascent()+p.descent())/2,p);}
            public void setAlpha(int alpha){}public void setColorFilter(android.graphics.ColorFilter cf){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}};
    }
    private void choosePhoto(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,PICK_IMAGE);}catch(Exception e){toast("Image picker unavailable");}}
    private void clearPhoto(){Uri old=photoUri;photoUri=null;if(old!=null){
        try{getContentResolver().releasePersistableUriPermission(old,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
    }}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();
            try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            if(photoUri!=null&&!photoUri.equals(u))clearPhoto();
            photoUri=u;save();showPage(1);
        }}
    private void applyDialog(){new AlertDialog.Builder(this).setTitle("Apply wallpaper")
        .setItems(new String[]{"Home screen","Lock screen","Home and lock screens"},(dialog,which)->renderAndUse(which,false)).setNegativeButton("Cancel",null).show();}
    private void exportWallpaper(){renderAndUse(0,true);}
    private Bitmap targetBitmap() throws IOException {
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(m);
        int width=Math.min(m.widthPixels,1280),height=(int)((long)width*m.heightPixels/Math.max(1,m.widthPixels));
        height=Math.max(1280,Math.min(height,3000));
        if(photoUri==null)return WallpaperArt.bitmap(width,height,wallpaper,amoled);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(photoUri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid photo");
        int sample=1;while(bounds.outWidth/sample>width || bounds.outHeight/sample>height)sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;
        try(InputStream in=getContentResolver().openInputStream(photoUri)){
            Bitmap photo=BitmapFactory.decodeStream(in,null,opts);if(photo==null)throw new IOException("Unsupported image");
            Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);
            float scale=Math.max((float)width/photo.getWidth(),(float)height/photo.getHeight());float rw=photo.getWidth()*scale,rh=photo.getHeight()*scale;
            Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);c.drawBitmap(photo,null,new RectF((width-rw)/2,(height-rh)/2,(width+rw)/2,(height+rh)/2),p);photo.recycle();return result;
        }
    }
    private void renderAndUse(int target,boolean export){toast(export?"Saving image…":"Applying wallpaper…");
        new Thread(()->{Bitmap bitmap=null;String outcome;
            try{bitmap=targetBitmap();if(export){ContentValues vals=new ContentValues();
                    vals.put(MediaStore.Images.Media.DISPLAY_NAME,"AetherShell_"+System.currentTimeMillis()+".png");vals.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                    vals.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AetherShell");vals.put(MediaStore.Images.Media.IS_PENDING,1);
                    Uri location=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,vals);if(location==null)throw new IOException("Could not create image");
                    boolean good=false;try(OutputStream out=getContentResolver().openOutputStream(location)){if(out==null)throw new IOException("Storage not writable");
                        good=bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}if(!good){getContentResolver().delete(location,null,null);throw new IOException("PNG export failed");}
                    vals.clear();vals.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(location,vals,null,null);
                    outcome="Saved in Pictures/AetherShell";
                }else{WallpaperManager wm=WallpaperManager.getInstance(this);if(!wm.isWallpaperSupported()||!wm.isSetWallpaperAllowed())throw new IOException("Wallpaper changes are disabled");
                    int flag=target==0?WallpaperManager.FLAG_SYSTEM:target==1?WallpaperManager.FLAG_LOCK:WallpaperManager.FLAG_SYSTEM|WallpaperManager.FLAG_LOCK;
                    wm.setBitmap(bitmap,null,false,flag);outcome="Wallpaper applied";
                }}catch(Exception e){outcome="Failed: "+(e.getMessage()==null?"Unknown error":e.getMessage());}
            finally{if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
            String message=outcome;handler.post(()->{if(!destroyed)toast(message);});
        },"AetherWallpaperWorker").start();}
    @Override public void onBackPressed(){if(page!=0)showPage(0);else super.onBackPressed();}
}
