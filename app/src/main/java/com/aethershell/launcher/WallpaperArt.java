package com.aethershell.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Offline deterministic compositions. All painting stops when this view is not visible. */
public final class WallpaperArt {
    public static final String[] NAMES = {"Aurora", "Nocturne", "Silk", "Horizon", "Orbit", "Bloom", "Tide", "Starlight", "Prism", "Mist", "Amethyst", "Ember"};
    private static final int[][] COLORS = {
            {0xff07111e,0xff163b74,0xff50e0cf}, {0xff070712,0xff20244a,0xff9369d9},
            {0xff130d29,0xff51317f,0xffed9bd2}, {0xff0c2236,0xff1d6686,0xffffc3aa},
            {0xff040811,0xff24375b,0xff8ccbe9}, {0xff141329,0xff684e99,0xffffb1cf},
            {0xff031b29,0xff126781,0xff5bddcb}, {0xff050917,0xff262c58,0xffb3baff},
            {0xff0b1331,0xff334993,0xff8d9bff}, {0xff17202e,0xff41546d,0xffc9d5e6},
            {0xff0d0b22,0xff40317c,0xffc7a5ff}, {0xff200e19,0xff7b3044,0xffffad77}
    };
    private WallpaperArt() {}

    public static int[] colors(int index) { return COLORS[Math.floorMod(index, COLORS.length)].clone(); }

    /** Draws without bitmap allocations; shapes scale to every screen ratio. */
    public static void render(Canvas canvas, int width, int height, int style, boolean amoled) {
        if (width <= 0 || height <= 0) return;
        final int idx = Math.floorMod(style, NAMES.length);
        int[] c = COLORS[idx];
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float w = width, h = height;
        if (amoled) c[0] = Color.BLACK;
        p.setShader(new LinearGradient(0,0,w*.78f,h, c[0], c[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,p); p.setShader(null);

        p.setShader(new RadialGradient(w*.68f,h*.27f,Math.max(w,h)*.74f,
                new int[]{withAlpha(c[2], idx==7?115:170), withAlpha(c[1],95), Color.TRANSPARENT},
                new float[]{0f,.36f,1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,p); p.setShader(null);

        if (idx==0 || idx==2 || idx==5 || idx==6 || idx==10) {
            for (int k=0;k<6;k++) {
                Path path=new Path();
                float base=h*(.34f+k*.115f), drift=w*((k%2==0)?-.28f:.35f);
                path.moveTo(-w*.25f,base+drift*.08f);
                path.cubicTo(w*.31f,base-h*.17f,w*.62f,base+h*.2f,w*1.30f,base-h*.12f);
                p.setColor(withAlpha(k%2==0 ? c[2]:0xffeef6ff, 12+(5-k)*7));
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w*(.012f+k*.006f));
                canvas.drawPath(path,p);
            }
            p.setStyle(Paint.Style.FILL);
        } else if(idx==4 || idx==7 || idx==1) {
            p.setStyle(Paint.Style.STROKE);
            for(int k=0;k<7;k++){
                p.setColor(withAlpha(c[2], 46-k*5));p.setStrokeWidth(Math.max(1,w*.0025f));
                canvas.drawCircle(w*.64f,h*.44f,w*(.17f+k*.087f),p);
            }
            p.setStyle(Paint.Style.FILL);
        } else if(idx==8) {
            for (int k=0;k<11;k++) {
                Path tri=new Path();float x=(k%4)*w*.36f-w*.15f,y=(k/4)*h*.38f-h*.15f;
                tri.moveTo(x,y);tri.lineTo(x+w*.40f,y+h*.11f);tri.lineTo(x+w*.14f,y+h*.35f);tri.close();
                p.setColor(withAlpha(k%2==0?c[2]:0xfffaf4ff, 15+(k%3)*8));canvas.drawPath(tri,p);
            }
        } else if(idx==3 || idx==9 || idx==11) {
            for(int k=0;k<7;k++){
                Path path=new Path();float y=h*(.48f+k*.095f);
                path.moveTo(0,y);path.cubicTo(w*.35f,y-h*.15f,w*.58f,y+h*.12f,w,y-h*.065f);
                path.lineTo(w,h);path.lineTo(0,h);path.close();
                p.setColor(withAlpha(k%2==0?c[2]:c[1], 12+(7-k)*8));canvas.drawPath(path,p);
            }
        }
        int seed=98123+idx*137;
        int stars = idx==7?100:idx==1||idx==4?43:12;
        for (int k=0;k<stars;k++) {
            seed=seed*1664525+1013904223;float x=((seed>>>8)&0xffff)/65535f*w;
            seed=seed*1664525+1013904223;float y=((seed>>>8)&0xffff)/65535f*h;
            float r=Math.max(.75f,w*((k%9==0)? .0022f:.0009f));
            p.setColor(Color.argb(35+(k%4)*25,250,248,255));canvas.drawCircle(x,y,r,p);
        }
    }
    private static int withAlpha(int color, int alpha){return (color & 0x00ffffff) | (Math.max(0,Math.min(alpha,255))<<24);}

    public static Bitmap bitmap(int width,int height,int style,boolean amoled){
        Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);render(c,width,height,style,amoled);return b;
    }

    public static class Preview extends View {
        private int style;private boolean amoled;
        public Preview(Context c,int style,boolean amoled){super(c);this.style=style;this.amoled=amoled;setLayerType(View.LAYER_TYPE_NONE,null);}
        public void setStyle(int s,boolean a){style=s;amoled=a;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);render(c,getWidth(),getHeight(),style,amoled);}
    }
}
