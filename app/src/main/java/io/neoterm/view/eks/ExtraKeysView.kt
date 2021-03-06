package io.neoterm.view.eks

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.*
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ToggleButton
import io.neoterm.R
import io.neoterm.customize.eks.EksConfigParser
import io.neoterm.ui.term.event.ToggleImeEvent
import io.neoterm.view.eks.button.ControlButton
import io.neoterm.view.eks.button.IExtraButton
import io.neoterm.view.eks.button.RepeatableButton
import io.neoterm.view.eks.button.StatedControlButton
import io.neoterm.view.eks.impl.ArrowButton
import org.greenrobot.eventbus.EventBus

class ExtraKeysView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    companion object {
        private val ESC = ControlButton(IExtraButton.KEY_ESC)
        private val TAB = ControlButton(IExtraButton.KEY_TAB)
        private val PAGE_UP = ControlButton(IExtraButton.KEY_PAGE_UP)
        private val PAGE_DOWN = ControlButton(IExtraButton.KEY_PAGE_DOWN)
        private val HOME = ControlButton(IExtraButton.KEY_HOME)
        private val END = ControlButton(IExtraButton.KEY_END)
        private val ARROW_UP = ArrowButton(IExtraButton.KEY_ARROW_UP)
        private val ARROW_DOWN = ArrowButton(IExtraButton.KEY_ARROW_DOWN)
        private val ARROW_LEFT = ArrowButton(IExtraButton.KEY_ARROW_LEFT)
        private val ARROW_RIGHT = ArrowButton(IExtraButton.KEY_ARROW_RIGHT)
        private val TOGGLE_IME = object : ControlButton(IExtraButton.KEY_TOGGLE_IME) {
            override fun onClick(view: View) {
                EventBus.getDefault().post(ToggleImeEvent())
            }
        }

        private val MAX_BUTTONS_PER_LINE = 7
        private val DEFAULT_ALPHA = 0.8f
        private val EXPANDED_ALPHA = 0.5f
    }

    private val builtinKeys = mutableListOf<IExtraButton>()
    private val userKeys = mutableListOf<IExtraButton>()

    private val buttonBars: MutableList<LinearLayout> = mutableListOf()
    private var typeface: Typeface? = null

    // Initialize StatedControlButton here
    // For avoid memory and context leak.
    private val CTRL = StatedControlButton(IExtraButton.KEY_CTRL)
    private val ALT = StatedControlButton(IExtraButton.KEY_ALT)

    private var buttonPanelExpanded = false
    private val EXPAND_BUTTONS = object : ControlButton(IExtraButton.KEY_SHOW_ALL_BUTTONS) {
        override fun onClick(view: View) {
            expandButtonPanel()
        }
    }

    init {
        alpha = DEFAULT_ALPHA
        gravity = Gravity.TOP
        orientation = LinearLayout.VERTICAL

        initBuiltinKeys()
        loadDefaultUserKeys()
        updateButtons()
        expandButtonPanel(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            if (buttonPanelExpanded) {
                expandButtonPanel()
                return true
            }
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

    fun setTextColor(textColor: Int) {
        IExtraButton.NORMAL_TEXT_COLOR = textColor
        updateButtons()
    }

    fun setTypeface(typeface: Typeface?) {
        this.typeface = typeface
        updateButtons()
    }

    fun readControlButton(): Boolean {
        return CTRL.readState()
    }

    fun readAltButton(): Boolean {
        return ALT.readState()
    }

    fun addUserKey(button: IExtraButton) {
        addKeyButton(userKeys, button)
    }

    fun addBuiltinKey(button: IExtraButton) {
        addKeyButton(builtinKeys, button)
    }

    fun clearUserKeys() {
        userKeys.clear()
    }

    fun loadDefaultUserKeys() {
        val defaultFile = ExtraKeysUtils.getDefaultFile()
        clearUserKeys()

        try {
            val parser = EksConfigParser()
            parser.setInput(defaultFile)
            val config = parser.parse()
            userKeys.addAll(config.shortcutKeys)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateButtons() {
        for (bar in buttonBars) {
            bar.removeAllViews()
        }

        var targetButtonBarIndex = 0
        for ((index, value) in builtinKeys.plus(userKeys).withIndex()) {
            addKeyButton(getButtonBarOrNew(targetButtonBarIndex), value)
            targetButtonBarIndex = (index + 1) / MAX_BUTTONS_PER_LINE
        }
        updateButtonBars()
    }

    private fun updateButtonBars() {
        removeAllViews()
        buttonBars.asReversed()
                .forEach {
                    addView(it)
                }
    }

    private fun expandButtonPanel(forceSetExpanded: Boolean? = null) {
        if (buttonBars.size <= 2) {
            return
        }

        buttonPanelExpanded = forceSetExpanded ?: !buttonPanelExpanded
        val visibility = if (buttonPanelExpanded) View.VISIBLE else View.GONE
        alpha = if (buttonPanelExpanded) EXPANDED_ALPHA else DEFAULT_ALPHA

        for (i in 2..buttonBars.size - 1) {
            buttonBars[i].visibility = visibility
        }
    }

    private fun createNewButtonBar(): LinearLayout {
        val line = LinearLayout(context)
        line.gravity = Gravity.START
        line.orientation = LinearLayout.HORIZONTAL
        line.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        return line
    }

    private fun getButtonBarOrNew(position: Int): LinearLayout {
        if (position >= buttonBars.size) {
            for (i in 0..(position - buttonBars.size + 1)) {
                buttonBars.add(createNewButtonBar())
            }
        }
        return buttonBars[position]
    }

    private fun addKeyButton(buttons: MutableList<IExtraButton>?, button: IExtraButton) {
        if (buttons != null && !buttons.contains(button)) {
            buttons.add(button)
        }
    }

    private fun addKeyButton(contentView: LinearLayout, extraButton: IExtraButton) {
        val outerButton = extraButton.makeButton(context, null, android.R.attr.buttonBarButtonStyle)

        val param = GridLayout.LayoutParams()
        param.setGravity(Gravity.CENTER)
        param.width = calculateButtonWidth()
        param.height = context.resources.getDimensionPixelSize(R.dimen.eks_height)
        param.topMargin = 0
        param.rightMargin = 0
        param.leftMargin = 0
        param.bottomMargin = 0

        outerButton.layoutParams = param
        outerButton.typeface = typeface
        outerButton.text = extraButton.buttonText
        outerButton.setTextColor(IExtraButton.NORMAL_TEXT_COLOR)
        outerButton.setAllCaps(false)

        outerButton.setOnClickListener {
            outerButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val root = rootView
            extraButton.onClick(root)
        }
        contentView.addView(outerButton)
    }

    private fun initBuiltinKeys() {
        addBuiltinKey(ESC)
        addBuiltinKey(TAB)
        addBuiltinKey(PAGE_DOWN)
        addBuiltinKey(ARROW_LEFT)
        addBuiltinKey(ARROW_DOWN)
        addBuiltinKey(ARROW_RIGHT)
        addBuiltinKey(TOGGLE_IME)

        addBuiltinKey(CTRL)
        addBuiltinKey(ALT)
        addBuiltinKey(PAGE_UP)
        addBuiltinKey(HOME)
        addBuiltinKey(ARROW_UP)
        addBuiltinKey(END)
        addBuiltinKey(EXPAND_BUTTONS)
    }

    private fun calculateButtonWidth(): Int {
        return context.resources.displayMetrics.widthPixels / ExtraKeysView.MAX_BUTTONS_PER_LINE
    }
}
