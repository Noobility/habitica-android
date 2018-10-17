package com.habitrpg.android.habitica.ui.adapter.inventory

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.events.commands.OpenGemPurchaseFragmentCommand
import com.habitrpg.android.habitica.ui.helpers.bindView
import com.habitrpg.android.habitica.extensions.inflate
import com.habitrpg.android.habitica.extensions.notNull
import com.habitrpg.android.habitica.models.inventory.Item
import com.habitrpg.android.habitica.models.shops.Shop
import com.habitrpg.android.habitica.models.shops.ShopCategory
import com.habitrpg.android.habitica.models.shops.ShopItem
import com.habitrpg.android.habitica.models.user.User
import com.habitrpg.android.habitica.ui.viewHolders.SectionViewHolder
import com.habitrpg.android.habitica.ui.viewHolders.ShopItemViewHolder
import com.habitrpg.android.habitica.ui.views.NPCBannerView
import org.greenrobot.eventbus.EventBus


class ShopRecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items: MutableList<Any> = ArrayList()
    private var shopIdentifier: String? = null
    private var ownedItems: Map<String, Item> = HashMap()


    var shopSpriteSuffix: String = ""
    set(value) {
        field = value
        notifyItemChanged(0)
    }
    var context: Context? = null
    var user: User? = null
    set(value) {
        field = value
        this.notifyDataSetChanged()
    }
    private var pinnedItemKeys: List<String> = ArrayList()

    var gearCategories: MutableList<ShopCategory> = ArrayList()
    set(value) {
        field = value
        notifyDataSetChanged()
    }

    internal var selectedGearCategory: String = ""
    set(value) {
        field = value
        if (field != "") {
            notifyDataSetChanged()
        }
    }

    private val emptyViewResource: Int
        get() = when (this.shopIdentifier) {
            Shop.SEASONAL_SHOP -> R.layout.empty_view_seasonal_shop
            Shop.TIME_TRAVELERS_SHOP -> R.layout.empty_view_timetravelers
            else -> R.layout.simple_textview
        }

    fun setShop(shop: Shop?) {
        if (shop == null) {
            return
        }
        shopIdentifier = shop.identifier
        items.clear()
        items.add(shop)
        for (category in shop.categories) {
            if (category.items.size > 0) {
                items.add(category)
                for (item in category.items) {
                    item.categoryIdentifier = category.identifier
                    items.add(item)
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            when (viewType) {
                0 -> {
                    val view = parent.inflate(R.layout.shop_header)
                    ShopHeaderViewHolder(view)
                }
                1 -> {
                    val view = parent.inflate(R.layout.shop_section_header)
                    SectionViewHolder(view)
                }
                2 -> {
                    val view = parent.inflate(emptyViewResource)
                    EmptyStateViewHolder(view)
                }
                else -> {
                    val view = parent.inflate(R.layout.row_shopitem)
                    val viewHolder = ShopItemViewHolder(view)
                    viewHolder.shopIdentifier = shopIdentifier
                    viewHolder
                }
            }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val obj = getItem(position)
        if (obj != null) {
            when (obj.javaClass) {
                Shop::class.java -> (obj as? Shop).notNull { (holder as? ShopHeaderViewHolder)?.bind(it, shopSpriteSuffix) }
                ShopCategory::class.java -> {
                    val category = obj as? ShopCategory
                    val sectionHolder = holder as? SectionViewHolder ?: return
                    sectionHolder.bind(category?.text ?: "")
                    if (gearCategories.contains(category)) {
                        context.notNull {context ->
                            val adapter = HabiticaClassArrayAdapter(context, R.layout.class_spinner_dropdown_item, gearCategories.map { it.identifier })
                            sectionHolder.spinnerAdapter = adapter
                            sectionHolder.selectedItem = gearCategories.indexOf(category)
                            sectionHolder.spinnerSelectionChanged = {
                                if (selectedGearCategory != gearCategories[holder.selectedItem].identifier) {
                                    selectedGearCategory = gearCategories[holder.selectedItem].identifier
                                }
                            }
                            if (user?.stats?.habitClass != category?.identifier) {
                                sectionHolder.notesView?.text = context.getString(R.string.class_gear_disclaimer)
                                sectionHolder.notesView?.visibility = View.VISIBLE
                            } else {
                                sectionHolder.notesView?.visibility = View.GONE
                            }
                        }
                    } else {
                        sectionHolder.spinnerAdapter = null
                        sectionHolder.notesView?.visibility = View.GONE
                    }
                }
                ShopItem::class.java -> {
                    val item = obj as? ShopItem ?: return
                    val itemHolder = holder as? ShopItemViewHolder ?: return
                    itemHolder.bind(item, item.canAfford(user))
                    if (ownedItems.containsKey(item.key+"-"+item.pinType)) {
                        itemHolder.itemCount = ownedItems[item.key+"-"+item.pinType]?.owned ?: 0
                    }
                    itemHolder.isPinned = pinnedItemKeys.contains(item.key)
                }
                String::class.java -> (holder as? EmptyStateViewHolder)?.text = obj as? String
            }
        }
    }

    @Suppress("ReturnCount")
    private fun getItem(position: Int): Any? {
        if (items.size == 0) {
            return null
        }
        if (position == 0) {
            return items[0]
        }
        if (position <= getGearItemCount()) {
            return when {
                position == 1 -> {
                    val category = getSelectedShopCategory()
                    category?.text = context?.getString(R.string.class_equipment) ?: ""
                    category
                }
                getSelectedShopCategory()?.items?.size ?: 0 <= position-2 -> return context?.getString(R.string.equipment_empty)
                else -> getSelectedShopCategory()?.items?.get(position-2)
            }
        } else {
            val itemPosition = position - getGearItemCount()
            if (itemPosition > items.size-1) {
                return null
            }
            return items[itemPosition]
        }
    }

    override fun getItemViewType(position: Int): Int = when(getItem(position)?.javaClass) {
        Shop::class.java -> 0
        ShopCategory::class.java -> 1
        ShopItem::class.java -> 3
        else -> 2
    }

    override fun getItemCount(): Int {
        val size = items.size + getGearItemCount()
        return if (size == 1) {
            2
        } else size
    }

    private fun getGearItemCount(): Int {
        return if (selectedGearCategory == "") {
            0
        } else {
            val selectedCategory: ShopCategory? = getSelectedShopCategory()
            if (selectedCategory != null) {
                if (selectedCategory.items.size == 0) {
                    2
                } else {
                    selectedCategory.items.size+1
                }
            } else {
                0
            }
        }
    }

    private fun getSelectedShopCategory() =
            gearCategories.firstOrNull { selectedGearCategory == it.identifier }

    fun setOwnedItems(ownedItems: Map<String, Item>) {
        this.ownedItems = ownedItems
        this.notifyDataSetChanged()
    }

    fun setPinnedItemKeys(pinnedItemKeys: List<String>) {
        this.pinnedItemKeys = pinnedItemKeys
        this.notifyDataSetChanged()
    }

    internal class ShopHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val descriptionView: TextView by bindView(itemView, R.id.descriptionView)
        private val npcBannerView: NPCBannerView by bindView(itemView, R.id.npcBannerView)
        private val namePlate: TextView by bindView(itemView, R.id.namePlate)

        init {
            descriptionView.movementMethod = LinkMovementMethod.getInstance()
        }

        fun bind(shop: Shop, shopSpriteSuffix: String) {
            npcBannerView.shopSpriteSuffix = shopSpriteSuffix
            npcBannerView.identifier = shop.identifier

            @Suppress("DEPRECATION")
            descriptionView.text = Html.fromHtml(shop.notes)
            namePlate.setText(shop.npcNameResource)
        }

    }

    class EmptyStateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val subscribeButton: Button? by bindView(itemView, R.id.subscribeButton)
        private val textView: TextView? by bindView(itemView, R.id.textView)
        init {
            subscribeButton?.setOnClickListener { EventBus.getDefault().post(OpenGemPurchaseFragmentCommand()) }
        }

        var text: String? = null
        set(value) {
            field = value
            textView?.text = field
        }
    }
}
