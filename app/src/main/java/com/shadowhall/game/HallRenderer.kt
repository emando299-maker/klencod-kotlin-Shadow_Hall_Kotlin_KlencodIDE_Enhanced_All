package com.shadowhall.game

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/** Lightweight OpenGL ES 2.0 third-person prototype. Art and AI are intentionally placeholder-level. */
class HallRenderer : GLSurfaceView.Renderer {
    @Volatile var moveX = 0f
    @Volatile var moveZ = 0f
    @Volatile var cameraYaw = 0f
    @Volatile var hidden = false
    @Volatile var flashlightOn = true
    @Volatile var stamina = 100f
    @Volatile var keysCount = 0
    @Volatile var statusText = "اجمع 3 مفاتيح"
    @Volatile var monsterState = "يتجول"
    @Volatile var gameWon = false
    private var program = 0
    private var posHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private lateinit var vertices: FloatBuffer
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private var px = 0f; private var pz = 2.8f
    private var mx = 0f; private var mz = -3.2f
    private val keys = arrayOf(floatArrayOf(-2.3f, -2.4f), floatArrayOf(2.2f, -0.7f), floatArrayOf(0.2f, 3.5f))
    private val collected = BooleanArray(3)
    private var lastTime = System.nanoTime()
    private var gameOver = false
    private var alertTimer = 0f
    private var patrolClock = 0f
    private val hideSpots = arrayOf(floatArrayOf(-2.8f, -0.4f), floatArrayOf(2.5f, 2.0f), floatArrayOf(0f, -3.8f))

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.035f, 0.07f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertexShader = "attribute vec4 aPosition; uniform mat4 uMVP; void main(){ gl_Position=uMVP*aPosition; }"
        val fragmentShader = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }"
        program = GLES20.glCreateProgram().also { p ->
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShader); val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        }
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val data = floatArrayOf(
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    }
    private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { s -> GLES20.glShaderSource(s, source); GLES20.glCompileShader(s) }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,62f,width.toFloat()/height.coerceAtLeast(1),0.1f,60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime(); val dt = ((now-lastTime)/1e9f).coerceIn(0f,0.04f); lastTime=now
        if (!gameOver) update(dt)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yaw = Math.toRadians(cameraYaw.toDouble())
        val bx = px - kotlin.math.sin(yaw).toFloat()*5.2f
        val bz = pz + kotlin.math.cos(yaw).toFloat()*5.2f
        Matrix.setLookAtM(view,0,bx,4.0f,bz,px,0.7f,pz,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        // floor and perimeter walls
        box(0f,-0.22f,0f,8f,0.35f,12f,0.13f,0.15f,0.21f)
        for (z in -5..5 step 2) box(0f,-0.035f,z.toFloat(),7.7f,0.012f,0.025f,0.22f,0.24f,0.30f)
        for (z in -4..4 step 4) {
            box(0f,2.18f,z.toFloat(),1.0f,0.04f,0.35f,0.52f,0.38f,0.16f)
            box(-1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
            box(1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
        }
        box(-4f,1f,0f,0.3f,2.4f,12f,0.20f,0.22f,0.30f)
        box(4f,1f,0f,0.3f,2.4f,12f,0.20f,0.22f,0.30f)
        box(0f,1f,-6f,8f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(0f,1f,6f,8f,2.4f,0.3f,0.16f,0.18f,0.25f)
        // cartoon furniture that can provide visual cover
        box(-2.8f,0.45f,-0.4f,1.0f,0.9f,1.2f,0.28f,0.12f,0.10f)
        box(2.5f,0.6f,2.0f,1.2f,1.2f,0.8f,0.18f,0.20f,0.27f)
        box(0f,0.5f,-3.8f,1.5f,1.0f,0.7f,0.25f,0.18f,0.11f)
        // player: colorful low-poly block character placeholder
        if (!hidden) {
            box(px,0.42f,pz,0.48f,0.8f,0.42f,0.18f,0.62f,0.82f)
            box(px,0.98f,pz,0.38f,0.35f,0.36f,0.95f,0.70f,0.48f)
        }
        // Monster with a more recognizable head, arms and glowing eyes
        box(mx,0.62f,mz,0.72f,1.25f,0.62f,0.25f,0.035f,0.09f)
        box(mx,1.36f,mz+0.02f,0.46f,0.42f,0.42f,0.12f,0.025f,0.055f)
        box(mx-0.47f,0.67f,mz,0.18f,0.82f,0.20f,0.18f,0.025f,0.06f)
        box(mx+0.47f,0.67f,mz,0.18f,0.82f,0.20f,0.18f,0.025f,0.06f)
        box(mx-0.13f,1.40f,mz+0.235f,0.085f,0.065f,0.035f,1f,0.03f,0.02f)
        box(mx+0.13f,1.40f,mz+0.235f,0.085f,0.065f,0.035f,1f,0.03f,0.02f)
        for (i in keys.indices) if (!collected[i]) {
            val bob=0.28f+kotlin.math.sin((System.nanoTime()/250000000.0+i).toFloat())*0.04f
            box(keys[i][0],bob,keys[i][1],0.22f,0.22f,0.22f,1f,0.72f,0.08f)
        }
        box(0f,1f,-5.72f,1.8f,2.0f,0.12f,if(keysCount>=3) 0.12f else 0.24f,if(keysCount>=3) 0.8f else 0.12f,0.18f)
        if (flashlightOn && !hidden) box(px,0.15f,pz-1.05f,0.25f,0.025f,1.6f,0.48f,0.44f,0.23f)
    }

    private fun update(dt: Float) {
        val yaw=Math.toRadians(cameraYaw.toDouble())
        val cy=kotlin.math.cos(yaw).toFloat(); val sy=kotlin.math.sin(yaw).toFloat()
        val moving=kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>0.08f
        val sprint=moving && stamina>1f && kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>1.25f
        val speed=if(sprint) 3.25f else 2.3f
        stamina=if(sprint) (stamina-26f*dt).coerceAtLeast(0f) else (stamina+13f*dt).coerceAtMost(100f)
        val noise=if(sprint) 1f else if(moving) 0.45f else 0f
        val dx=(moveX*cy-moveZ*sy)*speed*dt; val dz=(moveZ*cy+moveX*sy)*speed*dt
        if(!hidden && !gameWon) {
            val nx=(px+dx).coerceIn(-3.45f,3.45f); val nz=(pz+dz).coerceIn(-5.3f,5.3f)
            val blocked=(nx < -2.0f && nx > -3.55f && nz > -1.25f && nz < 0.45f) || (nx > 1.75f && nx < 3.25f && nz > 1.25f && nz < 2.75f) || (nx > -0.95f && nx < 0.95f && nz > -4.35f && nz < -3.25f)
            if(!blocked) { px=nx; pz=nz }
        }
        if(!hidden && !gameWon) {
            val vx=px-mx; val vz=pz-mz; val d=sqrt(vx*vx+vz*vz).coerceAtLeast(0.001f)
            patrolClock+=dt
            if((noise>0.65f && d<5.8f) || d<4.2f) alertTimer=2.5f else alertTimer=(alertTimer-dt).coerceAtLeast(0f)
            monsterState=if(alertTimer>0f) "يطاردك!" else "يتجول"
            if(alertTimer>0f) { mx+=vx/d*1.05f*dt; mz+=vz/d*1.05f*dt }
            else { mx+=kotlin.math.sin(patrolClock*0.65f)*0.22f*dt; mz+=kotlin.math.cos(patrolClock*0.45f)*0.18f*dt }
            if(d<0.72f) { stamina=(stamina-30f).coerceAtLeast(0f); px=0f; pz=3.2f; mx=0f; mz=-3.2f; alertTimer=0f }
        } else if(hidden) { monsterState="يدور عليك"; mx+=(0f-mx)*dt*0.08f; mz+=(-3.2f-mz)*dt*0.08f }
        for(i in keys.indices) if(!collected[i]) { val kx=px-keys[i][0]; val kz=pz-keys[i][1]; if(kx*kx+kz*kz<0.55f) collected[i]=true }
        keysCount=collected.count { it }
        if(keysCount>=3 && pz < -4.8f && kotlin.math.abs(px)<1.25f) { gameWon=true; monsterState="نجوت!" }
        statusText=when { gameWon -> "نجحت في الهروب!"; hidden -> "مختبئ — الوحش يبحث عنك"; keysCount>=3 -> "افتح بوابة الهروب عند نهاية الممر"; else -> "المفاتيح: $keysCount/3 • $monsterState" }
    }

    fun toggleHide() {
        if(hidden) { hidden=false; return }
        if(hideSpots.any { val dx=px-it[0]; val dz=pz-it[1]; dx*dx+dz*dz<1.65f }) hidden=true
    }

    fun restartGame() {
        px=0f; pz=2.8f; mx=0f; mz=-3.2f
        for(i in collected.indices) collected[i]=false
        hidden=false; flashlightOn=true; stamina=100f; keysCount=0; gameWon=false; gameOver=false; alertTimer=0f
    }

    private fun box(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        Matrix.setIdentityM(model,0); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniform4f(colorHandle,r,g,b,1f)
        vertices.position(0); GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
