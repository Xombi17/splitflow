package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.LineItemTag
import com.example.model.ParsedReceipt
import com.example.model.ReceiptLineItem
import com.google.firebase.ai.type.Schema
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Two-tier Receipt OCR pipeline:
 * 1. Tier 1 (Instant On-Device): ML Kit Text Recognition with spatial Y-row alignment (~150-250ms).
 * 2. Tier 2 (Cloud AI Fallback): MultiProviderAiRouter — Gemini 3.8 Flash -> Gemini 3.5 Flash-Lite
 *    multimodal vision (or BYOK round-robin text structuring) when ML Kit yields messy/low-line text
 *    or when the user explicitly requests Cloud AI refinement.
 */
object LocalReceiptOcrEngine {

    private val receiptJsonSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "merchantName" to Schema.string("Restaurant or store name"),
                "date" to Schema.string("ISO date YYYY-MM-DD"),
                "currency" to Schema.enumeration(listOf("INR", "USD", "EUR", "GBP", "THB")),
                "category" to Schema.enumeration(
                    listOf("FOOD", "TRAVEL", "STAY", "GROCERIES", "UTILITIES", "ENTERTAINMENT", "SHOPPING", "OTHER")
                ),
                "subtotal" to Schema.double("Subtotal in major currency units"),
                "tax" to Schema.double("Total GST/VAT/tax in major currency units"),
                "serviceCharge" to Schema.double("Service charge or tip in major currency units"),
                "discount" to Schema.double("Discount amount in major currency units"),
                "grandTotal" to Schema.double("Final payable total in major currency units"),
                "lineItems" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "name" to Schema.string("Item name"),
                            "quantity" to Schema.integer("Quantity, default 1"),
                            "totalPrice" to Schema.double("Line total in major currency units"),
                            "tag" to Schema.enumeration(
                                listOf("VEG", "NON_VEG", "BEER", "ALCOHOL", "MOCKTAIL", "DESSERT", "HOOKAH_EXTRA", "SHARED")
                            )
                        )
                    ),
                    description = "Individual priced line items on the receipt"
                )
            ),
            optionalProperties = listOf("date", "subtotal", "tax", "serviceCharge", "discount")
        )
    }

    suspend fun analyzeReceiptBitmap(
        context: Context,
        bitmap: Bitmap,
        router: MultiProviderAiRouter? = null,
        preferCloudAi: Boolean = false
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        val scaled = scaleBitmap(bitmap, maxDim = 1200)

        // Tier 1: Instant on-device ML Kit spatial OCR (~150-250ms)
        val mlStartMs = System.currentTimeMillis()
        val mlLines = runCatching { runMlKitSpatialOcr(scaled) }.getOrDefault(emptyList())
        val mlElapsedMs = (System.currentTimeMillis() - mlStartMs).coerceAtLeast(1L)

        val localParsed = if (mlLines.isNotEmpty()) {
            runCatching { parseReceiptLines(mlLines) }.getOrNull()
        } else null

        val localLooksGood = localParsed != null &&
            localParsed.lineItems.size >= 2 &&
            localParsed.grandTotalMinor > 0L

        if (!preferCloudAi && localLooksGood && localParsed != null) {
            router?.recordOcrRun(
                label = "ML Kit Spatial OCR",
                tier = EngineTier.ON_DEVICE_EDGE,
                detail = "Extracted ${localParsed.lineItems.size} items in ${mlElapsedMs}ms on-device"
            )
            return@withContext localParsed
        }

        // Tier 2: Cloud AI Fallback (Gemini 3.8 Flash -> 3.5 Flash-Lite multimodal, or BYOK structuring)
        if (router != null && router.keyStore.routingStrategy.value != RoutingStrategy.ON_DEVICE_ONLY) {
            try {
                val cloudReceipt = analyzeWithCloudFallback(scaled, mlLines, router)
                if (cloudReceipt != null) {
                    return@withContext cloudReceipt
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (localParsed != null) {
                    router.recordOcrRun(
                        label = "ML Kit Spatial OCR",
                        tier = EngineTier.ON_DEVICE_EDGE,
                        detail = "Extracted ${localParsed.lineItems.size} item(s) locally",
                        notice = "Cloud fallback unavailable: ${e.localizedMessage?.take(80)}"
                    )
                    return@withContext localParsed
                }
                throw e
            }
        }

        if (localParsed != null) {
            router?.recordOcrRun(
                label = "ML Kit Spatial OCR",
                tier = EngineTier.ON_DEVICE_EDGE,
                detail = "Processed ${mlLines.size} lines in ${mlElapsedMs}ms on-device"
            )
            return@withContext localParsed
        }

        throw IllegalStateException("No readable prices found on this receipt. Try a clearer photo or tap Cloud AI Scan.")
    }

    private suspend fun analyzeWithCloudFallback(
        bitmap: Bitmap,
        mlLines: List<String>,
        router: MultiProviderAiRouter
    ): ParsedReceipt? {
        val prompt = """
            Extract this receipt accurately into JSON.
            Return merchantName, date (YYYY-MM-DD), currency (INR/USD/EUR/GBP/THB), category, subtotal, tax (sum of CGST/SGST/GST/VAT), serviceCharge, discount, grandTotal, and lineItems (name, quantity, totalPrice, tag).
            Classify food/drink tags: VEG, NON_VEG, BEER, ALCOHOL, MOCKTAIL, DESSERT, HOOKAH_EXTRA, or SHARED.
            All prices must be numeric values in major currency units (e.g., 450.00).
        """.trimIndent()

        // If user prioritizes BYOK text keys and ML Kit read text lines, try BYOK round-robin structuring first
        val strategy = router.keyStore.routingStrategy.value
        val hasEnabledByok = router.keyStore.keys.value.any { it.enabled }
        if (strategy == RoutingStrategy.BYOK_FIRST_THEN_FIREBASE && hasEnabledByok && mlLines.size >= 2) {
            val byokRes = runCatching {
                router.generateStructuredOrText(
                    systemPrompt = "You are a strict receipt JSON parser. Return valid JSON only with keys: merchantName, date, currency, category, subtotal, tax, serviceCharge, discount, grandTotal, lineItems[{name,quantity,totalPrice,tag}].",
                    userPrompt = "$prompt\n\nOCR LINES:\n${mlLines.joinToString("\n")}",
                    expectJson = true,
                    firebaseSchema = receiptJsonSchema
                )
            }.getOrNull()
            if (byokRes != null) {
                val parsed = runCatching { parseCloudReceiptJson(byokRes.text) }.getOrNull()
                if (parsed != null) {
                    router.recordOcrRun(
                        label = byokRes.engineLabel,
                        tier = byokRes.tier,
                        detail = "Structured ${parsed.lineItems.size} items in ${byokRes.latencyMs}ms",
                        notice = byokRes.fallbackNote
                    )
                    return parsed
                }
            }
        }

        // Multimodal vision fallback via Firebase AI Logic (gemini-3.8-flash -> gemini-3.5-flash-lite)
        val visionRes = router.generateMultimodalJson(
            bitmap = bitmap,
            prompt = prompt,
            schema = receiptJsonSchema
        )
        val parsed = parseCloudReceiptJson(visionRes.text)
        router.recordOcrRun(
            label = visionRes.engineLabel,
            tier = visionRes.tier,
            detail = "Vision extracted ${parsed.lineItems.size} items in ${visionRes.latencyMs}ms",
            notice = visionRes.fallbackNote
        )
        return parsed
    }

    internal fun parseCloudReceiptJson(jsonString: String): ParsedReceipt {
        val obj = JSONObject(jsonString)
        val merchant = obj.optString("merchantName").trim().ifBlank { "Scanned Receipt" }
        val dateIso = obj.optString("date").trim().takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currency = CurrencyCode.fromRaw(obj.optString("currency", "INR"))
        val category = ExpenseCategory.fromRaw(obj.optString("category", "FOOD"))

        val itemsArr = obj.optJSONArray("lineItems")
        val items = mutableListOf<ReceiptLineItem>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(i) ?: continue
                val name = itemObj.optString("name").trim()
                val totalMajor = itemObj.optDouble("totalPrice", 0.0)
                if (name.isBlank() || totalMajor <= 0.0) continue
                val qty = itemObj.optLong("quantity", 1L).coerceIn(1L, 50L)
                val totalMinor = (totalMajor * 100.0).roundToLong().coerceAtLeast(1L)
                val unitMinor = if (qty > 1L) totalMinor / qty else totalMinor
                val tag = LineItemTag.fromRaw(itemObj.optString("tag", "SHARED"))
                items.add(
                    ReceiptLineItem(
                        itemId = "ai_ocr_${items.size + 1}",
                        name = name,
                        quantity = qty,
                        unitPriceMinor = unitMinor,
                        totalPriceMinor = totalMinor,
                        assignedMemberIds = emptyList(),
                        itemTag = tag.name
                    )
                )
            }
        }

        val itemsSum = items.sumOf { it.totalPriceMinor }
        val subtotalMinor = (obj.optDouble("subtotal", 0.0) * 100.0).roundToLong().let {
            if (it > 0L) it else itemsSum
        }
        val taxMinor = (obj.optDouble("tax", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        val serviceMinor = (obj.optDouble("serviceCharge", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        val discountMinor = (obj.optDouble("discount", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        val grandTotalMinor = (obj.optDouble("grandTotal", 0.0) * 100.0).roundToLong().let {
            if (it > 0L) it else (subtotalMinor + taxMinor + serviceMinor - discountMinor).coerceAtLeast(itemsSum)
        }

        if (items.isEmpty() && grandTotalMinor > 0L) {
            items.add(
                ReceiptLineItem(
                    itemId = "ai_ocr_1",
                    name = merchant,
                    quantity = 1L,
                    unitPriceMinor = grandTotalMinor,
                    totalPriceMinor = grandTotalMinor,
                    assignedMemberIds = emptyList(),
                    itemTag = LineItemTag.SHARED.name
                )
            )
        }
        require(grandTotalMinor > 0L) { "Receipt JSON contained no valid total amount." }

        return ParsedReceipt(
            merchantName = merchant,
            date = dateIso,
            currency = currency,
            category = category,
            lineItems = items,
            subtotalMinor = subtotalMinor.coerceAtLeast(itemsSum),
            taxMinor = taxMinor,
            serviceChargeMinor = serviceMinor,
            discountMinor = discountMinor,
            grandTotalMinor = grandTotalMinor
        )
    }

    private suspend fun runMlKitSpatialOcr(bitmap: Bitmap): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { err ->
                    if (cont.isActive) cont.resumeWithException(err)
                }
        }

        data class PositionedLine(val text: String, val centerY: Float, val leftX: Float, val height: Float)

        val positioned = visionText.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val txt = line.text.trim()
                if (txt.isEmpty()) null
                else PositionedLine(
                    text = txt,
                    centerY = (box.top + box.bottom) / 2f,
                    leftX = box.left.toFloat(),
                    height = (box.bottom - box.top).coerceAtLeast(12).toFloat()
                )
            }
        }.sortedBy { it.centerY }

        if (positioned.isEmpty()) {
            return visionText.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }

        val rows = mutableListOf<MutableList<PositionedLine>>()
        for (item in positioned) {
            val matchRow = rows.lastOrNull { row ->
                val avgY = row.map { it.centerY }.average().toFloat()
                val avgH = row.map { it.height }.average().toFloat()
                abs(item.centerY - avgY) <= avgH * 0.65f
            }
            if (matchRow != null) {
                matchRow.add(item)
            } else {
                rows.add(mutableListOf(item))
            }
        }

        return rows.map { row ->
            row.sortedBy { it.leftX }.joinToString("  ") { it.text }
        }
    }

    internal fun parseReceiptLines(lines: List<String>): ParsedReceipt {
        val joined = lines.joinToString("\n")
        val lowerAll = joined.lowercase(Locale.US)

        val currency = detectCurrency(lowerAll)
        val category = detectReceiptCategory(lowerAll)
        val dateIso = extractDateIso(joined) ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val merchantName = extractMerchantName(lines)

        val priceTailRegex = Regex(
            "(?:₹|\\$|€|£|฿|rs\\.?|inr)?\\s*(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)\\s*$"
        )

        var subtotalMinor = 0L
        var taxMinor = 0L
        var serviceMinor = 0L
        var discountMinor = 0L
        var grandTotalMinor = 0L

        val items = mutableListOf<ReceiptLineItem>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.length < 3) continue
            val priceMatch = priceTailRegex.find(line) ?: continue
            val amountVal = priceMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (amountVal <= 0.0 || amountVal > 500_000.0) continue
            val minor = (amountVal * 100.0).roundToLong()

            val label = line.substring(0, priceMatch.range.first)
                .trim()
                .trimEnd(':', '-', '.', '*', '₹', '$', '€', '£', '฿')
                .trim()
            val labelLower = label.lowercase(Locale.US)

            when {
                isMetaNoiseLine(labelLower) -> continue

                Regex("\\b(grand\\s*total|net\\s*payable|amount\\s*payable|total\\s*payable|bill\\s*total|final\\s*total|net\\s*amount)\\b")
                    .containsMatchIn(labelLower) || labelLower == "total" -> {
                    if (minor >= grandTotalMinor) grandTotalMinor = minor
                }

                Regex("\\b(sub\\s*total|subtotal|item\\s*total|food\\s*total)\\b").containsMatchIn(labelLower) -> {
                    subtotalMinor = minor
                }

                Regex("\\b(cgst|sgst|igst|gst|vat|sales\\s*tax|tax)\\b").containsMatchIn(labelLower) -> {
                    taxMinor += minor
                }

                Regex("\\b(service\\s*charge|service\\s*fee|srv\\s*chg|s\\.c\\.|tip|gratuity)\\b").containsMatchIn(labelLower) -> {
                    serviceMinor += minor
                }

                Regex("\\b(discount|offer|coupon|promo|savings|less)\\b").containsMatchIn(labelLower) -> {
                    discountMinor += minor
                }

                label.length >= 2 && label.any { it.isLetter() } -> {
                    val (qty, cleanName) = extractQuantityAndName(label)
                    val unitMinor = if (qty > 1L) minor / qty else minor
                    val tag = classifyLineItemTag(cleanName)
                    items.add(
                        ReceiptLineItem(
                            itemId = "local_ocr_${items.size + 1}",
                            name = cleanName,
                            quantity = qty,
                            unitPriceMinor = unitMinor,
                            totalPriceMinor = minor,
                            assignedMemberIds = emptyList(),
                            itemTag = tag.name
                        )
                    )
                }
            }
        }

        val itemsSum = items.sumOf { it.totalPriceMinor }
        if (subtotalMinor <= 0L) subtotalMinor = itemsSum

        if (grandTotalMinor <= 0L) {
            grandTotalMinor = (subtotalMinor + taxMinor + serviceMinor - discountMinor).coerceAtLeast(itemsSum)
        }

        if (items.isEmpty() && grandTotalMinor > 0L) {
            items.add(
                ReceiptLineItem(
                    itemId = "local_ocr_1",
                    name = merchantName,
                    quantity = 1L,
                    unitPriceMinor = grandTotalMinor,
                    totalPriceMinor = grandTotalMinor,
                    assignedMemberIds = emptyList(),
                    itemTag = LineItemTag.SHARED.name
                )
            )
            subtotalMinor = grandTotalMinor
        }

        if (grandTotalMinor <= 0L) {
            throw IllegalStateException("Could not detect prices on this receipt. Try cropping closer to the bill items.")
        }

        return ParsedReceipt(
            merchantName = merchantName,
            date = dateIso,
            currency = currency,
            category = category,
            lineItems = items,
            subtotalMinor = subtotalMinor,
            taxMinor = taxMinor,
            serviceChargeMinor = serviceMinor,
            discountMinor = discountMinor,
            grandTotalMinor = grandTotalMinor
        )
    }

    private fun extractMerchantName(lines: List<String>): String {
        val skip = listOf("tax invoice", "invoice", "receipt", "bill", "gstin", "phone", "tel:", "table", "order", "date", "cashier")
        for (raw in lines.take(6)) {
            val cleaned = raw.trim()
            val lower = cleaned.lowercase(Locale.US)
            if (cleaned.length in 3..42 &&
                cleaned.any { it.isLetter() } &&
                skip.none { lower.contains(it) } &&
                !Regex("^\\d+[\\s/:-]").containsMatchIn(cleaned)
            ) {
                return cleaned
            }
        }
        return "Scanned Receipt"
    }

    private fun extractDateIso(text: String): String? {
        Regex("\\b(20\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})\\b").find(text)?.let { m ->
            val y = m.groupValues[1]
            val mo = m.groupValues[2].padStart(2, '0')
            val d = m.groupValues[3].padStart(2, '0')
            return "$y-$mo-$d"
        }
        Regex("\\b(\\d{1,2})[-/](\\d{1,2})[-/](20\\d{2})\\b").find(text)?.let { m ->
            val d = m.groupValues[1].padStart(2, '0')
            val mo = m.groupValues[2].padStart(2, '0')
            val y = m.groupValues[3]
            return "$y-$mo-$d"
        }
        return null
    }

    private fun detectCurrency(lower: String): CurrencyCode = when {
        lower.contains("₹") || lower.contains("inr") || lower.contains("cgst") || lower.contains("sgst") || Regex("\\brs\\.?\\b").containsMatchIn(lower) -> CurrencyCode.INR
        lower.contains("$") || lower.contains("usd") -> CurrencyCode.USD
        lower.contains("€") || lower.contains("eur") -> CurrencyCode.EUR
        lower.contains("£") || lower.contains("gbp") -> CurrencyCode.GBP
        lower.contains("฿") || lower.contains("thb") -> CurrencyCode.THB
        else -> CurrencyCode.INR
    }

    private fun detectReceiptCategory(lower: String): ExpenseCategory = when {
        listOf("restaurant", "cafe", "kitchen", "bar", "grill", "bistro", "pizza", "biryani", "coffee", "paneer", "chicken", "cgst")
            .any { lower.contains(it) } -> ExpenseCategory.FOOD
        listOf("mart", "supermarket", "grocery", "fresh", "organic", "milk", "vegetables")
            .any { lower.contains(it) } -> ExpenseCategory.GROCERIES
        listOf("uber", "ola", "taxi", "cab", "airlines", "fuel", "petrol", "diesel", "parking")
            .any { lower.contains(it) } -> ExpenseCategory.TRAVEL
        listOf("hotel", "resort", "inn", "suites", "villa", "stay")
            .any { lower.contains(it) } -> ExpenseCategory.STAY
        listOf("cinema", "pvr", "inox", "tickets", "bowling", "club")
            .any { lower.contains(it) } -> ExpenseCategory.ENTERTAINMENT
        else -> ExpenseCategory.FOOD
    }

    private fun isMetaNoiseLine(labelLower: String): Boolean {
        val noise = listOf(
            "gstin", "fssai", "invoice", "bill no", "order no", "table", "date", "time",
            "cash", "card", "upi", "change", "tender", "balance", "round off", "rounding",
            "qty", "rate", "amount", "item name", "description", "phone", "mobile"
        )
        return noise.any { labelLower.contains(it) }
    }

    private fun extractQuantityAndName(label: String): Pair<Long, String> {
        val prefixQty = Regex("^(\\d+)\\s*[xX*]\\s*(.+)$").find(label)
        if (prefixQty != null) {
            val q = prefixQty.groupValues[1].toLongOrNull()?.coerceIn(1L, 50L) ?: 1L
            return q to prefixQty.groupValues[2].trim()
        }
        val leadingNum = Regex("^(\\d+)\\s+([A-Za-z].+)$").find(label)
        if (leadingNum != null) {
            val q = leadingNum.groupValues[1].toLongOrNull()?.coerceIn(1L, 25L) ?: 1L
            return q to leadingNum.groupValues[2].trim()
        }
        return 1L to label
    }

    private fun classifyLineItemTag(name: String): LineItemTag {
        val l = name.lowercase(Locale.US)
        return when {
            listOf("beer", "pint", "pitcher", "lager", "ipa", "draught", "kingfisher", "heineken", "corona", "bira", "hoegaarden").any { l.contains(it) } -> LineItemTag.BEER
            listOf("whisky", "whiskey", "vodka", "gin", "rum", "tequila", "wine", "cocktail", "negroni", "margarita", "martini", "shot", "peg", "liit", "mojito rum").any { l.contains(it) } -> LineItemTag.ALCOHOL
            listOf("mocktail", "virgin", "soda", "coke", "pepsi", "sprite", "lime", "juice", "coffee", "latte", "cappuccino", "tea", "chai", "shake", "water").any { l.contains(it) } -> LineItemTag.MOCKTAIL
            listOf("ice cream", "tiramisu", "cake", "brownie", "gulab", "dessert", "cheesecake", "pastry", "gelato").any { l.contains(it) } -> LineItemTag.DESSERT
            listOf("hookah", "shisha", "sheesha", "cover charge").any { l.contains(it) } -> LineItemTag.HOOKAH_EXTRA
            listOf("chicken", "mutton", "lamb", "prawn", "shrimp", "fish", "crab", "calamari", "pork", "bacon", "egg", "omelette", "meat", "beef", "non-veg", "non veg", "kebab").any { l.contains(it) } -> LineItemTag.NON_VEG
            listOf("paneer", "veg", "dal", "roti", "naan", "paratha", "mushroom", "gobi", "aloo", "pasta", "pizza", "fries", "salad", "rice", "biryani", "dosa", "idli", "samosa", "tofu").any { l.contains(it) } -> LineItemTag.VEG
            else -> LineItemTag.SHARED
        }
    }

    private fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(
            source,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
