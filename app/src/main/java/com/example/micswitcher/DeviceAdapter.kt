package com.example.micswitcher

import android.media.AudioDeviceInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onClick: (AudioDeviceInfo) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<AudioDeviceInfo>()
    private var selectedId: Int = -1

    fun submitList(devices: List<AudioDeviceInfo>) {
        items.clear()
        items.addAll(devices)
        notifyDataSetChanged()
    }

    fun setSelected(id: Int) {
        selectedId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = items[position]
        holder.bind(device, device.id == selectedId, onClick)
    }

    override fun getItemCount(): Int = items.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radio: RadioButton = itemView.findViewById(R.id.deviceRadio)
        private val label: TextView = itemView.findViewById(R.id.deviceLabel)

        fun bind(device: AudioDeviceInfo, selected: Boolean, onClick: (AudioDeviceInfo) -> Unit) {
            val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: MainActivity.typeLabel(device.type)
            label.text = name
            radio.isChecked = selected
            itemView.setOnClickListener { onClick(device) }
            radio.setOnClickListener { onClick(device) }
        }
    }
}
