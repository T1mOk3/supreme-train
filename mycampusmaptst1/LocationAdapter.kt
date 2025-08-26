package io.github.mycampusmaptst1

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

class LocationAdapter (
    private var locations: List<EachLocation> = emptyList()
) : RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {
//  when location is clicked
    private var itemClickListener: ((EachLocation) -> Unit)? = null
//  when btn is clicked
    private var onGoButtonClickListener: ((EachLocation) -> Unit)? = null
//
    private val glideOptions = RequestOptions()
        .placeholder(R.drawable.ic_placeholder)
        .error(R.drawable.ic_placeholder)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .override(200, 200)

//  receive clicked location
    fun setOnItemClickListener(listener: (EachLocation) -> Unit) {
        itemClickListener = listener
    }
//  references for each item
    inner class LocationViewHolder (itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.tvLocationName)
        val typeTextView: TextView = itemView.findViewById(R.id.tvLocationType)
        val openHoursTextView: TextView = itemView.findViewById(R.id.tvLocationOpenHours)
        val imageView: ImageView = itemView.findViewById(R.id.tvLocationImage)
        val btnGo: Button = itemView.findViewById(R.id.btnGo)
//      add data to view
        @SuppressLint("SetTextI18n")
        fun bind(location: EachLocation) {
//          for entire item
            itemView.setOnClickListener {
                itemClickListener?.invoke(location)
            }
//          set data
            nameTextView.text = location.name
            typeTextView.text = location.type
            openHoursTextView.text = location.openHours

            loadImage(location)

            btnGo.setOnClickListener {
                onGoButtonClickListener?.invoke(location)
            }
        }

        private fun loadImage(location: EachLocation) {
            when {
                location.imagePath.isEmpty() -> {
                    imageView.setImageResource(R.drawable.ic_placeholder)
                }
                location.imagePath.startsWith("drawable/") -> {
                    loadResourceImage(location.imagePath)
                }
                else -> {
                    imageView.setImageResource(R.drawable.ic_placeholder)
                }
            }
        }

        @SuppressLint("DiscouragedApi")
        private fun loadResourceImage(path: String) {
            // extract resource name from path
            val resName = path.replace("drawable/", "")
            //  get id
            val resId = itemView.resources.getIdentifier(
                resName,
                "drawable",
                itemView.context.packageName
            )
            // set image
            if (resId != 0) {
                Glide.with(itemView.context)
                    .load(resId)
                    .apply(glideOptions)
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.ic_placeholder)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
         holder.bind(locations[position])
    }


    //  total number of items in the set
    override fun getItemCount(): Int = locations.size

    fun updateData(newLocations: List<EachLocation>) {
        locations = newLocations
        notifyDataSetChanged()
    }

    fun setOnGoButtonClickListener(listener: (EachLocation) -> Unit) {
        onGoButtonClickListener = listener
    }
}

