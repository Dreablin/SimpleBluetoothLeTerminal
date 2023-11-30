package de.kai_morich.simple_bluetooth_le_terminal.viewmodel

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kai_morich.simple_bluetooth_le_terminal.R
import de.kai_morich.simple_bluetooth_le_terminal.SerialListener
import de.kai_morich.simple_bluetooth_le_terminal.SerialService
import de.kai_morich.simple_bluetooth_le_terminal.SerialSocket
import de.kai_morich.simple_bluetooth_le_terminal.TerminalFragment
import de.kai_morich.simple_bluetooth_le_terminal.TextUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import de.kai_morich.simple_bluetooth_le_terminal.TextUtil.HexWatcher

class TerminalFragmentViewModel(val context: Context): ViewModel(), SerialListener, ServiceConnection {

    private val _stateFlow = MutableSharedFlow<SpannableStringBuilder>()
    val stateFlow get() = _stateFlow

    enum class Connected {
        False, Pending, True
    }

    var deviceAddress: String? = null
    var connected = Connected.False
    var service: SerialService? = null
    var hexEnabled = false
    var newline = TextUtil.newline_crlf
    var pendingNewline = false
    lateinit var receiveText: TextView
    val receivedText2: MutableLiveData<String> = MutableLiveData("")
    var initialStart = true
    var hexWatcher: HexWatcher? = null

    override fun onSerialConnect() {
        status("Connected")
        connected = Connected.True
    }

    override fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
        receive(datas)
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
       receive(datas)
    }

    override fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as SerialService.SerialBinder).service
        (service as SerialService).attach(this)
        if (initialStart) {
            initialStart = false
             connect()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    fun status(str: String) {
        val spn = SpannableStringBuilder(
            """
                 $str
                 
                 """.trimIndent()
        )
        spn.setSpan(
            ForegroundColorSpan(context.getColor(R.color.colorStatusText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        viewModelScope.launch {
            _stateFlow.emit(spn)
        }
    }

    fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
    }

    private fun receive(datas: ArrayDeque<ByteArray>) {
        val spn = SpannableStringBuilder()
        for (data in datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n')
            } else {
                var msg = data.decodeToString() //TODO test this
                if (newline == TextUtil.newline_crlf && msg.length > 0) {
                    // don't show CR as ^M if directly before LF
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf)
                    // special handling if CR and LF come in separate fragments
                    if (pendingNewline && msg[0] == '\n') {
                        if (spn.length >= 2) {
                            spn.delete(spn.length - 2, spn.length)
                        } else {
                            val edt = receivedText2.value
                            if (edt != null && edt.length >= 2) {
                                edt.removeRange(edt.length - 2, edt.length)
                                receivedText2.value = edt ?: ""
                            }
                        }
                    }
                    pendingNewline = msg[msg.length - 1] == '\r'
                }
                spn.append(TextUtil.toCaretString(msg, newline.length != 0))
            }
        }
        receivedText2.value = receivedText2.value + spn
//        receiveText!!.append(spn)
    }

    fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            status("connecting...")
            connected = Connected.Pending
            val socket = SerialSocket(context, device) // application context!
            service!!.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    fun send(str: String) {
        if (connected != Connected.True) {
//            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val msg: String
            val data: ByteArray
            if (hexEnabled) {
                val sb = StringBuilder()
                TextUtil.toHexString(sb, TextUtil.fromHexString(str))
                TextUtil.toHexString(sb, newline.toByteArray())
                msg = sb.toString()
                data = TextUtil.fromHexString(msg)
            } else {
                msg = str
                data = (str + newline).toByteArray()
            }
            val spn = SpannableStringBuilder(
                """
                     $msg
                     
                     """.trimIndent()
            )
            spn.setSpan(
                ForegroundColorSpan(context.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
//            receiveText!!.append(spn)
            receivedText2.value = receivedText2.value + spn
            service!!.write(data)
        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }
}