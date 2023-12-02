package de.kai_morich.simple_bluetooth_le_terminal

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.kai_morich.simple_bluetooth_le_terminal.SerialService.SerialBinder
import de.kai_morich.simple_bluetooth_le_terminal.TextUtil.HexWatcher
import de.kai_morich.simple_bluetooth_le_terminal.viewmodel.TerminalFragmentViewModel
import de.kai_morich.simple_bluetooth_le_terminal.viewmodel.TerminalFragmentViewModel.Connected.*
import de.kai_morich.simple_bluetooth_le_terminal.viewmodel.TerminalFragmentViewModelFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Arrays

class TerminalFragment : Fragment() {

    val viewModel: TerminalFragmentViewModel by viewModels {
        TerminalFragmentViewModelFactory(requireActivity().applicationContext)
    }


//    private enum class Connected {
//        False, Pending, True
//    }

//    private var deviceAddress: String? = null
//    private var service: SerialService? = null
    private lateinit var receiveText: TextView
    private lateinit var sendText: TextView
//    private var hexWatcher: HexWatcher? = null
//    private var connected = Connected.False
//    private var initialStart = true
//    private var hexEnabled = false
//    private var pendingNewline = false
//    private var newline = TextUtil.newline_crlf

    /*
     * Lifecycle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        viewModel.deviceAddress = requireArguments().getString("device")

        lifecycleScope.launch {
            viewModel.stateFlow.collect {
                receiveText!!.append(it)
            }
        }
        viewModel.receivedText2.observe(this, Observer { receiveText.text = it })
    }

    override fun onDestroy() {
        if (viewModel.connected != False) viewModel.disconnect()
        requireActivity().stopService(Intent(activity, SerialService::class.java))
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.service != null) viewModel.service!!.attach(viewModel) else requireActivity().startService(
            Intent(
                activity,
                SerialService::class.java
            )
        ) // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    override fun onStop() {
        if (viewModel.service != null && !requireActivity().isChangingConfigurations) viewModel.service!!.detach()
        super.onStop()
    }

    @Suppress("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        requireActivity().bindService(
            Intent(getActivity(), SerialService::class.java),
            viewModel,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDetach() {
        try {
            requireActivity().unbindService(viewModel)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.initialStart && viewModel.service != null) {
            viewModel.initialStart = false
            requireActivity().runOnUiThread { viewModel.connect() }
        }
    }


    /*
     * UI
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText =
            view.findViewById(R.id.receive_text) // TextView performance decreases with number of spans
        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance())
        sendText = view.findViewById(R.id.send_text)
        viewModel.hexWatcher = HexWatcher(sendText)
        viewModel.hexWatcher!!.enable(viewModel.hexEnabled)
        sendText.addTextChangedListener(viewModel.hexWatcher)
        sendText.setHint(if (viewModel.hexEnabled) "HEX mode" else "")
        val sendBtn = view.findViewById<View>(R.id.send_btn)
        sendBtn.setOnClickListener { v: View? ->
            viewModel.send(
                sendText.getText().toString()
            )
        }
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
        menu.findItem(R.id.hex).isChecked = viewModel.hexEnabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.clear) {
            receiveText.text = ""
            true
        } else if (id == R.id.newline) {
            val newlineNames = resources.getStringArray(R.array.newline_names)
            val newlineValues = resources.getStringArray(R.array.newline_values)
            val pos = Arrays.asList(*newlineValues).indexOf(viewModel.newline)
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Newline")
            builder.setSingleChoiceItems(newlineNames, pos) { dialog: DialogInterface, item1: Int ->
                viewModel.newline = newlineValues[item1]
                dialog.dismiss()
            }
            builder.create().show()
            true
        } else if (id == R.id.hex) {
            viewModel.hexEnabled = !viewModel.hexEnabled
            sendText.text = ""
            viewModel.hexWatcher!!.enable(viewModel.hexEnabled)
            sendText.hint = if (viewModel.hexEnabled) "HEX mode" else ""
            item.isChecked = viewModel.hexEnabled
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}