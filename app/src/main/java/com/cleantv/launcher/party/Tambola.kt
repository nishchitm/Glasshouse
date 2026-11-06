package com.cleantv.launcher.party

/** Tambola (Housie) tickets, checks and the traditional number calls. */
object Tambola {
    /**
     * A standard ticket: 3 rows × 9 columns, 5 numbers per row (15 in all), every column used. Column c holds
     * numbers from its decade (1–9, 10–19, …, 80–90), sorted top to bottom.
     */
    fun ticket(): Ticket {
        while (true) {
            val rows = List(3) { (0 until 9).shuffled().take(5).toSet() }
            val perColumn = (0 until 9).map { c -> rows.count { c in it } }
            if (perColumn.any { it == 0 }) continue // every column needs at least one number
            val grid = Array(3) { arrayOfNulls<Int>(9) }
            for (c in 0 until 9) {
                val range = when (c) {
                    0 -> 1..9
                    8 -> 80..90
                    else -> c * 10..c * 10 + 9
                }
                val numbers = range.shuffled().take(perColumn[c]).sorted()
                var k = 0
                for (r in 0 until 3) if (c in rows[r]) grid[r][c] = numbers[k++]
            }
            return grid.map { it.toList() }
        }
    }

    fun check(ticket: Ticket, called: Set<Int>, claim: Claim): Boolean {
        fun row(i: Int) = ticket[i].filterNotNull()
        val all = ticket.flatten().filterNotNull()
        return when (claim) {
            Claim.EarlyFive -> all.count { it in called } >= 5
            Claim.TopLine -> row(0).all { it in called }
            Claim.MiddleLine -> row(1).all { it in called }
            Claim.BottomLine -> row(2).all { it in called }
            Claim.FullHouse -> all.all { it in called }
        }
    }

    /** The traditional name of a number, e.g. "Two fat ladies". */
    fun rhyme(n: Int): String = CALLS[n].orEmpty()

    /** What the caller says, in the housie way: "Two fat ladies, eight and eight, eighty eight". */
    fun call(n: Int): String = when {
        n < 10 -> "${rhyme(n)}, number $n"
        n % 10 == 0 -> "${rhyme(n)}, $n"
        else -> "${rhyme(n)}, ${n / 10} and ${n % 10}, $n"
    }

    private val CALLS = mapOf(
        1 to "Kelly's eye",
        2 to "One little duck",
        3 to "Cup of tea",
        4 to "Knock at the door",
        5 to "Man alive",
        6 to "Half a dozen",
        7 to "Lucky seven",
        8 to "One fat major",
        9 to "Doctor's orders",
        10 to "A big fat hen",
        11 to "Legs eleven",
        12 to "One dozen",
        13 to "Unlucky for some",
        14 to "Valentine's Day",
        15 to "Young and keen",
        16 to "Sweet sixteen",
        17 to "Dancing queen",
        18 to "Coming of age",
        19 to "Goodbye teens",
        20 to "One score",
        21 to "Key of the door",
        22 to "Two little ducks",
        23 to "Thee and me",
        24 to "Two dozen",
        25 to "Duck and dive",
        26 to "Pick and mix",
        27 to "Gateway to heaven",
        28 to "Overweight",
        29 to "Rise and shine",
        30 to "Flirty thirty",
        31 to "Get up and run",
        32 to "Buckle my shoe",
        33 to "All the threes",
        34 to "Ask for more",
        35 to "Jump and jive",
        36 to "Three dozen",
        37 to "More than eleven",
        38 to "Christmas cake",
        39 to "Steps",
        40 to "Life begins at forty",
        41 to "Time for fun",
        42 to "Winnie the Pooh",
        43 to "Down on your knees",
        44 to "All the fours",
        45 to "Halfway there",
        46 to "Up to tricks",
        47 to "Four and seven",
        48 to "Four dozen",
        49 to "PC",
        50 to "Half a century",
        51 to "Tweak of the thumb",
        52 to "Chicken vindaloo",
        53 to "Stuck in the tree",
        54 to "Clean the floor",
        55 to "All the fives",
        56 to "Was she worth it",
        57 to "Heinz varieties",
        58 to "Make them wait",
        59 to "Brighton line",
        60 to "Five dozen",
        61 to "Baker's bun",
        62 to "Turn the screw",
        63 to "Tickle me",
        64 to "Red raw",
        65 to "Old age pension",
        66 to "Clickety click",
        67 to "Stairway to heaven",
        68 to "Saving grace",
        69 to "Favourite of mine",
        70 to "Three score and ten",
        71 to "Bang on the drum",
        72 to "Six dozen",
        73 to "Queen bee",
        74 to "Hit the floor",
        75 to "Strive and strive",
        76 to "Trombones",
        77 to "Two little crutches",
        78 to "Heaven's gate",
        79 to "One more time",
        80 to "Eight and blank",
        81 to "Stop and run",
        82 to "Straight on through",
        83 to "Time for tea",
        84 to "Seven dozen",
        85 to "Staying alive",
        86 to "Between the sticks",
        87 to "Torquay in Devon",
        88 to "Two fat ladies",
        89 to "Nearly there",
        90 to "Top of the house",
    )
}
