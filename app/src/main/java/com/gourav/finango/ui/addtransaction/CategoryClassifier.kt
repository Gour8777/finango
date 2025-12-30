package com.gourav.finango.ui.addtransaction



import kotlin.math.max
import kotlin.math.min

/**
 * Tiny, offline, typo-tolerant categorizer for expense descriptions.
 * - Uses hard rules (brand/biller names, category keywords)
 * - Runs a small typo-correction pass (snap to nearest known keyword)
 * - Fuzzy scoring with Jaro–Winkler + Levenshtein
 * - Caps weak-signal inflation (Top-K)
 * - Learns new hints from user corrections (addKeywordsOnFeedback)
 *
 * Usage:
 *   val r = CategoryClassifier.classify("paid bescm elec bil")
 *   // r.category -> "Electricity"
 */
object CategoryClassifier {

    // Exposed category list (keep in sync with your app)
    val categories = listOf(
        "General", "Groceries", "Food & Drinks", "Furniture", "Rent",
        "Water", "Gifts", "Medical", "Maintenance", "Travel",
        "Movies", "Electricity", "Donation"
    )

    // ---- Seed lexicon (edit/extend freely; all lowercase) ----
    private val lexicon: MutableMap<String, MutableSet<String>> = mutableMapOf(
        "Groceries" to mutableSetOf(
            "grocery","groceries","veggies","vegetables","kirana","supermarket",
            "big bazaar","dmart","more","reliance fresh","milk","ration","provision","amazon min"
        ),
        "Food & Drinks" to mutableSetOf(
            "food","lunch","dinner","breakfast","snacks","drink","beverage",
            "tea","coffee","swiggy","zomato","restaurant","pizza","burger","pasta",
            "biriyani","biryani","juice","cafe","hotel food"
        ),
        "Furniture" to mutableSetOf(
            "furniture","sofa","chair","table","bed","mattress","cupboard",
            "wardrobe","dining","desk","cot","almirah","ikea"
        ),
        "Rent" to mutableSetOf(
            "rent","house rent","room rent","lease","pg","hostel fees","maintenance charges"
        ),
        "Water" to mutableSetOf(
            "water","water bill","borewell","can water","bisleri","aqua","mineral water","bwssb"
        ),
        "Gifts" to mutableSetOf(
            "gift","present","birthday gift","wedding gift","anniversary gift","hamper","return gift"
        ),
        "Medical" to mutableSetOf(
            "medical","medicine","pharmacy","chemist","tablet","syrup","doctor","clinic",
            "hospital","diagnostic","pathology","blood test","scan","rx","labtest","apollo","1mg"
        ),
        "Maintenance" to mutableSetOf(
            "maintenance","repair","service","fix","plumber","electrician","cleaning",
            "housekeeping","painting","ac service","amc","car service","bike service"
        ),
        "Travel" to mutableSetOf(
            "travel","cab","taxi","bus","train","flight","air","uber","ola","rapido",
            "metro","petrol","diesel","fuel","toll","ticket","hotel","booking","stay"
        ),
        "Movies" to mutableSetOf(
            "movie","film","cinema","theatre","theater","pvr","inox","bookmyshow","bms",
            "netflix","amazon prime","prime video","hotstar","ott"
        ),
        "Electricity" to mutableSetOf(
            "electricity","power bill","electric bill","current bill","energy bill",
            "bescom","tneb","kseb","mseb","mpev","kesc","pspcl"
        ),
        "Donation" to mutableSetOf(
            "donation","charity","zakat","tithe","ngo","temple","church","mosque","relief fund","donate"
        ),
        "General" to mutableSetOf(
            "shopping","purchase","online","order","upi","misc","general","others"
        )
    )

    // ---- Hard rules (high precision; immediate match) ----
    private val HARD_RULES: Map<String, Set<String>> = mapOf(
        "Groceries" to setOf("grocery","groceries","kirana","supermarket","ration","dmart","reliance fresh","big bazaar","more"),
        "Food & Drinks" to setOf("swiggy","zomato","restaurant","lunch","dinner","breakfast","snacks","tea","coffee","cafe"),
        "Electricity" to setOf("bescom","tneb","kseb","electricity","power bill","current bill","energy bill"),
        "Water" to setOf("bwssb","water bill","bisleri","can water","aquaguard","aqua"),
        "Travel" to setOf("uber","ola","rapido","metro","flight","train","cab","taxi","irctc","ticket","hotel"),
        "Movies" to setOf("bookmyshow","bms","pvr","inox","cinema","movie","netflix","hotstar","prime"),
        "Rent" to setOf("rent","house rent","room rent","pg","lease","hostel fees"),
        "Donation" to setOf("donation","charity","ngo","zakat","tithe"),
        "Medical" to setOf("pharmacy","chemist","medicine","tablet","syrup","rx","hospital","clinic","diagnostic","labtest","blood test","scan","1mg","apollo")
    )

    // Canonical keyword universe (for typo snapping)
    private val KEYWORDS: Set<String> by lazy {
        val fromHard = HARD_RULES.values.flatten()
        val fromLex = lexicon.values.flatten()
        (fromHard + fromLex).map { it.lowercase() }.toSet()
    }

    // Common words we never “correct” to keywords
    private val STOP_WORDS = setOf(
        "the","a","an","to","for","with","and","on","in","by","via","of",
        "bill","fees","paid","payment","online","upi","from"
    )

    data class Result(val category: String, val confidence: Double, val debug: Map<String, Double>)

    // -------------------- Public API --------------------
    fun classify(rawDescription: String): Result {
        val text = normalize(rawDescription)
        if (text.isBlank()) return Result("General", 0.0, emptyMap())

        val rawTokens = tokenize(text)

        // 1) Typo correction (snap near-matches to canonical keywords)
        val (tokens, ngrams) = correctedTokensAndNgrams(rawTokens)
        val tokenSet = tokens.toSet()

        // 2) Hard rules — immediate, high-confidence
        for ((cat, keys) in HARD_RULES) {
            val hit = keys.any { key ->
                if (key.contains(' ')) ngrams.any { it == key } else tokenSet.contains(key)
            }
            if (hit) return Result(cat, 0.98, mapOf(cat to 1.0))
        }

        // 3) If category name itself appears, honor it
        val directCat = categories.firstOrNull { cat ->
            val nameTok = cat.lowercase()
            tokenSet.contains(nameTok) || ngrams.any { it.contains(nameTok) }
        }
        if (directCat != null) return Result(directCat, 0.90, mapOf(directCat to 1.0))

        // 4) Fuzzy scoring with guardrails
        val SIM_CUTOFF = 0.80   // ignore weak matches
        val EXACT_BONUS = 0.35
        val PHRASE_WT = 1.25
        val TOP_K = 5

        val scores = mutableMapOf<String, Double>()
        for (cat in categories) {
            val kws = lexicon[cat] ?: emptySet()
            val contributions = mutableListOf<Double>()

            for (kw in kws) {
                var best = 0.0
                var exact = false
                val isPhrase = kw.contains(' ')

                for (ng in ngrams) {
                    if (ng == kw) { exact = true; best = 1.0; break }
                    val jw = jaroWinkler(kw, ng)
                    val lev = 1.0 - (levenshtein(kw, ng).toDouble() / max(kw.length, ng.length).coerceAtLeast(1))
                    val s = max(jw, lev)
                    if (s > best) best = s
                }
                if (!isPhrase && tokenSet.contains(kw)) { exact = true; best = 1.0 }

                if (best >= SIM_CUTOFF) {
                    var c = best * (if (isPhrase) PHRASE_WT else 1.0)
                    if (exact) c += EXACT_BONUS
                    contributions.add(c)
                }
            }

            contributions.sortDescending()
            val sum = contributions.take(TOP_K).sum()

            val prior = when (cat) {
                "General" -> -0.15
                "Medical", "Gifts" -> -0.05
                else -> 0.0
            }
            scores[cat] = sum + prior
        }

        val bestPair = scores.maxByOrNull { it.value }?.let { it.key to it.value } ?: ("General" to 0.0)
        val (bestCat, bestScore) = bestPair
        val sorted = scores.entries.sortedByDescending { it.value }
        val second = if (sorted.size > 1) sorted[1].value else 0.0
        val margin = (bestScore - second).coerceAtLeast(0.0)

        val conf = sigmoid(bestScore / 2.0) * 0.55 + sigmoid(margin * 3.0) * 0.45
        val MIN_BEST_SCORE = 0.60   // need some evidence
        val MIN_MARGIN = 0.15       // avoid ties / confusion
        val MIN_CONFIDENCE = 0.55   // require decent confidence

        val finalCat = when {
            bestScore < MIN_BEST_SCORE -> "General"
            margin < MIN_MARGIN        -> "General"
            conf < MIN_CONFIDENCE      -> "General"
            else                       -> bestCat
        }

        return Result(finalCat, conf, scores.toSortedMap(compareBy { it }))
    }

    /**
     * Optional: call after a user manually fixes a category to “teach” the classifier.
     * Persist `lexicon` if you want this to survive app restarts.
     */
    fun addKeywordsOnFeedback(description: String, correctCategory: String, minSimilarity: Double = 0.90) {
        val text = normalize(description)
        val tokens = tokenize(text)
        val (corrTokens, corrNgrams) = correctedTokensAndNgrams(tokens)
        val candidates = (corrTokens + corrNgrams).toMutableSet()

        val generic = setOf("the","and","for","with","bill","fees","paid","payment","online","upi","to","from","via","by")
        candidates.removeAll { it.length < 3 || it in generic }

        val set = lexicon.getOrPut(correctCategory) { mutableSetOf() }
        for (ng in candidates) {
            val maxOther = categories.filter { it != correctCategory }.maxOfOrNull { cat ->
                val kws = lexicon[cat] ?: emptySet()
                kws.maxOfOrNull { kw -> jaroWinkler(kw, ng) } ?: 0.0
            } ?: 0.0
            if (maxOther < 0.75) {
                val alreadyClose = categories.maxOf { cat ->
                    (lexicon[cat] ?: emptySet()).maxOfOrNull { kw -> jaroWinkler(kw, ng) } ?: 0.0
                }
                if (alreadyClose < minSimilarity && ng.length <= 24) {
                    set.add(ng)
                }
            }
        }
    }

    // -------------------- Typo correction helpers --------------------
    private fun correctedTokensAndNgrams(tokens: List<String>): Pair<List<String>, List<String>> {
        val correctedTokens = tokens.map { tok -> snapToKeyword(tok) ?: tok }
        val big = bigrams(correctedTokens).map { phrase -> snapToKeyword(phrase) ?: phrase }
        val tri = trigrams(correctedTokens).map { phrase -> snapToKeyword(phrase) ?: phrase }
        val ngrams = correctedTokens + big + tri
        return correctedTokens to ngrams
    }

    private fun snapToKeyword(s: String): String? {
        if (s.isBlank()) return null
        if (s in KEYWORDS) return s
        if (s in STOP_WORDS) return null

        var bestKw: String? = null
        var bestScore = 0.0
        val maxEd = when {
            s.length <= 4 -> 1
            s.length <= 8 -> 2
            else -> 3
        }

        for (kw in KEYWORDS) {
            if (kotlin.math.abs(kw.length - s.length) > maxEd) continue

            val ed = levenshtein(s, kw)
            if (ed <= maxEd) {
                val jw = jaroWinkler(s, kw)
                val levSim = 1.0 - (ed.toDouble() / max(s.length, kw.length))
                val score = (jw * 0.65) + (levSim * 0.35)
                if (score > bestScore) { bestScore = score; bestKw = kw }
            } else {
                val jw = jaroWinkler(s, kw)
                if (jw > bestScore) { bestScore = jw; bestKw = kw }
            }
        }
        return if (bestScore >= 0.86) bestKw else null
    }

    // -------------------- Text utils --------------------
    private fun normalize(s: String): String {
        val lowered = s.lowercase()
        val cleaned = lowered
            .replace(Regex("[^a-z0-9\\s&]"), " ") // keep letters, digits, spaces and &
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned
            .replace("elec bill", "electricity")
            .replace("power bill", "electricity")
            .replace("current bill", "electricity")
            .replace("fuel", "petrol")
            .replace("biryani", "biriyani")
    }

    private fun tokenize(s: String): List<String> =
        s.split(" ").filter { it.isNotBlank() }

    private fun bigrams(tokens: List<String>): List<String> =
        tokens.windowed(2, 1, partialWindows = false).map { it.joinToString(" ") }

    private fun trigrams(tokens: List<String>): List<String> =
        tokens.windowed(3, 1, partialWindows = false).map { it.joinToString(" ") }

    // -------------------- Similarities --------------------
    // Jaro-Winkler similarity (0..1)
    private fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val mtp = matches(s1, s2)
        val m = mtp[0].toDouble()
        if (m == 0.0) return 0.0
        val j = (m / s1.length + m / s2.length + (m - mtp[1]) / m) / 3.0
        val p = 0.1
        val l = min(mtp[2], 4).toDouble()
        return j + l * p * (1 - j)
    }

    // Helper for Jaro-Winkler
    private fun matches(s1: String, s2: String): IntArray {
        var maxS = s1
        var minS = s2
        if (s1.length < s2.length) { maxS = s2; minS = s1 }
        val range = max(maxS.length / 2 - 1, 0)
        val matchIndexes = IntArray(minS.length) { -1 }
        val matchFlags = BooleanArray(maxS.length)
        var matches = 0
        for (i in minS.indices) {
            val start = max(0, i - range)
            val end = min(i + range + 1, maxS.length)
            for (j in start until end) {
                if (!matchFlags[j] && minS[i] == maxS[j]) {
                    matchIndexes[i] = j
                    matchFlags[j] = true
                    matches++
                    break
                }
            }
        }
        val ms1 = CharArray(matches)
        val ms2 = CharArray(matches)
        var si = 0
        for (i in minS.indices) if (matchIndexes[i] != -1) { ms1[si] = minS[i]; si++ }
        si = 0
        for (j in maxS.indices) if (matchFlags[j]) { ms2[si] = maxS[j]; si++ }
        var transpositions = 0
        for (i in 0 until matches) if (ms1[i] != ms2[i]) transpositions++
        var prefix = 0
        for (i in 0 until min(minS.length, maxS.length)) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return intArrayOf(matches, transpositions / 2, prefix)
    }

    // Levenshtein distance
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = min(
                    min(dp[j] + 1, dp[j - 1] + 1),
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + kotlin.math.exp(-x))
}
