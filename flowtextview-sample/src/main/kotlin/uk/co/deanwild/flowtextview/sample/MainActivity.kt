package uk.co.deanwild.flowtextview.sample

import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import uk.co.deanwild.flowtextview.listeners.OnLinkClickListener
import uk.co.deanwild.flowtextview.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        val flowTextView = binding.flowTextView
        flowTextView.text = latinText()
        flowTextView.ellipsize = TextUtils.TruncateAt.END
        flowTextView.onLinkClickListener = OnLinkClickListener { url ->
            Toast.makeText(this, getString(R.string.link_clicked, url), Toast.LENGTH_SHORT).show()
        }

        binding.smaller.setOnClickListener { changeTextSize(-2f) }
        binding.larger.setOnClickListener { changeTextSize(+2f) }

        // Justification needs API 26.
        binding.justify.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        binding.justify.setOnCheckedChangeListener { _, checked ->
            flowTextView.justificationMode = if (checked) JUSTIFICATION_MODE_INTER_WORD else JUSTIFICATION_MODE_NONE
        }

        // Lines are counted across the whole flow, beside and below the images.
        binding.maxLines.setOnCheckedChangeListener { _, checked ->
            flowTextView.maxLines = if (checked) MAX_LINES else Int.MAX_VALUE
        }

        // Hiding a child reflows the text into the space it occupied.
        binding.images.setOnCheckedChangeListener { _, checked ->
            binding.image1.isVisible = checked
            binding.image2.isVisible = checked
        }

        // Children aligned to start/end mirror with the layout direction; the text follows.
        binding.rtl.setOnCheckedChangeListener { _, checked ->
            flowTextView.layoutDirection = if (checked) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            flowTextView.text = if (checked) hebrewText() else latinText()
        }
    }

    private fun changeTextSize(deltaSp: Float) {
        val view = binding.flowTextView
        val pixelsPerSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
        val currentSp = view.textSize / pixelsPerSp
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, (currentSp + deltaSp).coerceIn(10f, 40f))
    }

    private fun latinText(): Spanned = HtmlCompat.fromHtml(getString(R.string.lorem), HtmlCompat.FROM_HTML_MODE_LEGACY)

    private fun hebrewText(): Spanned =
        HtmlCompat.fromHtml(getString(R.string.hebrew).repeat(40), HtmlCompat.FROM_HTML_MODE_LEGACY)

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private companion object {
        // LineBreaker.JUSTIFICATION_MODE_* (API 29) share their values with the API 26 Layout constants.
        const val JUSTIFICATION_MODE_NONE = 0
        const val JUSTIFICATION_MODE_INTER_WORD = 1

        const val MAX_LINES = 12
    }
}
