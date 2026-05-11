package com.safeg.adapters

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.safeg.R
import com.safeg.models.GetStaffPassByStaffNoOrNameResponseItem


class StaffAdapter(
    private val staffList: MutableList<GetStaffPassByStaffNoOrNameResponseItem>,
    private val onItemClicked: (GetStaffPassByStaffNoOrNameResponseItem) -> Unit
) : RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {

    inner class StaffViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val tvStaffNo: TextView = view.findViewById(R.id.tvStaffNo)
        val ivProfile: ImageView = view.findViewById(R.id.ivProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_staff_card, parent, false)
        return StaffViewHolder(view)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        val staff = staffList[position]
        holder.tvName.text = staff.name
        if (!staff.photo.isNullOrEmpty()) {
            try {
                val decodedBytes = Base64.decode(staff.photo, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                holder.ivProfile.setImageBitmap(bitmap)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                // optionally set a placeholder image if decoding fails
//                holder.ivProfile.setImageResource(R.drawable.ic_placeholder)
            }
            }
        holder.tvPhone.text = "📞 ${staff.mobileNo}"
        holder.tvStaffNo.text = "Staff No: ${staff.username}"

        holder.itemView.setOnClickListener {
            onItemClicked(staff)
        }
    }

    override fun getItemCount() = staffList.size
}
