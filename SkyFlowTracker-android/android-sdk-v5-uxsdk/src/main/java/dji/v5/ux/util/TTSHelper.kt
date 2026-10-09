package dji.v5.ux.util

import android.content.Context
import android.nfc.Tag
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

//文字转语音工具类
class TTSHelper private constructor(context: Context){
    private val TAG = "TTSHelper"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    companion object{
        @Volatile
        private var instance: TTSHelper? = null

        fun getInstance(context: Context): TTSHelper {
            return instance?: synchronized(this){
                instance ?: TTSHelper(context.applicationContext).also { instance = it }
            }}
    }

    init {
        tts = TextToSpeech(context) {status ->
            if (status == TextToSpeech.SUCCESS){
                //设置语言为中文
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                    Log.e(TAG, "不支持中文")
                    //尝试使用英文
                    tts?.setLanguage(Locale.US)
                }else{
                    isInitialized = true
                    Log.d(TAG, "初始化TTS成功")
                }
                //设置倍速
                tts?.setSpeechRate(1.0f)
                //设置音调
                tts?.setPitch(1.0f)
                //设置播放监听器
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener(){
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "播放开始")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "播放完成")
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "播放错误")
                    }

                })
            }else{
                Log.e(TAG, "初始化TTS失败")
            }
        }
    }

    /**
     * 播放语音
     * @param text 语音内容
     * @param queueMode 播放队列模式，默认为添加到队列末尾,QUEUE_ADD为添加到队列末尾，QUEUE_FLUSH为清空队列
     */
    fun speak(text: String,queueMode: Int = TextToSpeech.QUEUE_ADD){
        if (!isInitialized){
            Log.d(TAG, "TTS未初始化，请稍后再试")
            return
        }

        Log.d(TAG, "播放语音：$text")
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,text)
        tts?.speak(text,queueMode,params,text)
    }

    //立即播放
    fun speakNow(text: String){
        speak(text,TextToSpeech.QUEUE_FLUSH)
    }

    //停止播放
    fun stop(){
        tts?.stop()
    }

    //释放资源
    fun shutDown(){
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        Log.d(TAG, "释放TTS资源")
    }

    //检查tts是否可用
    fun isAvailable(): Boolean = isInitialized


}