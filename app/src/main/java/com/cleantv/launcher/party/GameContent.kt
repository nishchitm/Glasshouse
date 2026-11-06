package com.cleantv.launcher.party

import android.content.Context
import com.cleantv.launcher.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

@Serializable
data class EmojiPuzzle(val emojis: String, val answer: String, val year: Int? = null, val story: String = "", val song: String = "", val level: String = "", val joke: String = "") {
    /** What a right answer is worth before any clues: the harder, the more. */
    val basePoints get() = when (level) {
        "Easy" -> 1.0
        "Hard" -> 3.0
        else -> 2.0
    }

    /** The answer with only each word's first letter showing: "O _ _   S _ _ _ _ _". */
    val titleHint: String
        get() = answer.split(" ").joinToString("    ") { word ->
            word.mapIndexed { i, c -> if (i == 0 || !c.isLetter()) c.toString() else "_" }.joinToString(" ")
        }
}

/** One Filmi Bluff round: a true fact with a blank, plus two lies of ours for when few people are playing. */
data class Bluff(val prompt: String, val answer: String, val decoys: List<String>)

/** One Guess the Frame round. */
data class Frame(val film: String, val year: Int?, val url: String, val choices: List<String>) {
    val right get() = choices.indexOf(film)
}

/** The buzzer games. Emoji Movies shows emojis; the rest show text. */
enum class GuessMode(val label: String, val isText: Boolean = false) {
    Movies("Emoji Ka Tamasha"),
    Dialogues("Dialogue-Baazi", true),
    Cast("Starring?", true),
    Plots("Plot Ka Satyanash", true),
}

/** Hand-written film puzzles and titles for game night. */
class GameContent(context: Context, private val repo: TmdbRepository?) {
    private val prefs = context.getSharedPreferences("party", Context.MODE_PRIVATE)

    /**
     * Hand-written sets rather than generated ones: the clue has to be a real joke or line from the film, and they
     * need no network at the party. Ones played before come last, so repeats wait until the rest are used.
     */
    fun puzzles(mode: GuessMode): List<EmojiPuzzle> {
        val all = when (mode) {
            GuessMode.Movies -> EMOJI.map { it.copy(level = levelOf(it.answer)) }
            GuessMode.Dialogues -> DIALOGUES.map { (line, film) -> filmPuzzle(line, film) }
            GuessMode.Cast -> CAST.map { (cast, film) -> filmPuzzle(cast, film) }
            GuessMode.Plots -> PLOTS.map { (plot, film, level, joke) -> filmPuzzle(plot, film).copy(level = level, joke = joke) }
        }
        val played = prefs.getStringSet(mode.name, emptySet()).orEmpty()
        if (all.all { it.answer + it.emojis in played }) prefs.edit().remove(mode.name).apply()
        val (old, fresh) = all.shuffled().partition { it.answer + it.emojis in played }
        return fresh + old
    }

    /** Remembered between parties. */
    fun markPlayed(mode: GuessMode, puzzle: EmojiPuzzle) {
        prefs.edit().putStringSet(mode.name, prefs.getStringSet(mode.name, emptySet()).orEmpty() + (puzzle.answer + puzzle.emojis)).apply()
    }

    /** How well known the film is, which is what mostly decides how hard it is to guess. */
    private fun levelOf(film: String) = when (film) {
        in EASY_FILMS -> "Easy"
        in HARD_FILMS -> "Hard"
        else -> "Medium"
    }

    /** A puzzle shown as text, with the film's song and story hints where the emoji list has them. */
    private fun filmPuzzle(text: String, film: String): EmojiPuzzle {
        val known = EMOJI.firstOrNull { it.answer == film }
        return EmojiPuzzle(text, film, known?.year, known?.story.orEmpty(), known?.song.orEmpty(), levelOf(film))
    }

    /** Song, then film, for Hum It. Song titles only, never lyrics. */
    fun humTitles(): List<String> = HUM.map { (song, film) -> "$song  ·  $film" }.shuffled()

    /** Unplayed ones first, like the puzzles. */
    fun bluffs(): List<Bluff> {
        val all = BLUFFS.map { (prompt, answer, a, b) -> Bluff(prompt, answer, listOf(a, b)) }
        val played = prefs.getStringSet(BLUFF_KEY, emptySet()).orEmpty()
        if (all.all { it.prompt in played }) prefs.edit().remove(BLUFF_KEY).apply()
        val (old, fresh) = all.shuffled().partition { it.prompt in played }
        return fresh + old
    }

    fun markBluffPlayed(bluff: Bluff) {
        prefs.edit().putStringSet(BLUFF_KEY, prefs.getStringSet(BLUFF_KEY, emptySet()).orEmpty() + bluff.prompt).apply()
    }

    /** Stills for the chosen films, unplayed films first. Needs the internet, unlike the other games. */
    suspend fun frames(count: Int = 25): List<Frame> = coroutineScope {
        val repo = repo ?: return@coroutineScope emptyList()
        val played = prefs.getStringSet(FRAME_KEY, emptySet()).orEmpty()
        if (EMOJI.all { it.answer in played }) prefs.edit().remove(FRAME_KEY).apply()
        val (old, fresh) = EMOJI.shuffled().partition { it.answer in played }
        val gate = Semaphore(6)
        (fresh + old).take(count + 5).map { film ->
            async {
                gate.withPermit {
                    runCatching { repo.filmStill(film.answer, film.year) }.getOrNull()?.let { Frame(film.answer, film.year, it, choicesFor(film.answer)) }
                }
            }
        }.awaitAll().filterNotNull().take(count)
    }

    /** The film and three others from the same list, shuffled. */
    private fun choicesFor(film: String): List<String> =
        (EMOJI.map { it.answer }.filter { it != film }.shuffled().take(3) + film).shuffled()

    fun markFramePlayed(frame: Frame) {
        prefs.edit().putStringSet(FRAME_KEY, prefs.getStringSet(FRAME_KEY, emptySet()).orEmpty() + frame.film).apply()
    }

    /** The same chosen films as the emoji game. TMDB's list has English titles ("Like Stars on Earth") and niche picks. */
    fun charadesTitles(): List<String> = EMOJI.map { it.answer }.shuffled()

    private companion object {
        val EASY_FILMS = setOf("3 Idiots", "Dilwale Dulhania Le Jayenge", "Kuch Kuch Hota Hai", "Kabhi Khushi Kabhie Gham", "Kal Ho Naa Ho", "Mohabbatein", "Hera Pheri", "Phir Hera Pheri", "Munna Bhai M.B.B.S.", "Lage Raho Munna Bhai", "Om Shanti Om", "Jab We Met", "Dhoom", "Dhoom 2", "Dhoom 3", "Dabangg", "Singham", "PK", "Dangal", "Bajrangi Bhaijaan", "Chennai Express", "Yeh Jawaani Hai Deewani", "Zindagi Na Milegi Dobara", "Lagaan", "Gadar", "Pathaan", "Jawan", "Animal", "Stree", "Welcome", "Don", "Devdas", "Hum Aapke Hain Koun", "Krrish", "Koi... Mil Gaya", "Chak De! India", "Aashiqui 2", "Kabir Singh", "Housefull", "Golmaal: Fun Unlimited", "Karan Arjun", "Gadar 2", "Andaz Apna Apna")
        val HARD_FILMS = setOf("Partner", "Hum Tum", "Love Aaj Kal", "Garam Masala", "Salaam Namaste", "Kaminey", "Delhi Belly", "Rock On!!", "Band Baaja Baaraat", "Once Upon a Time in Mumbaai", "Ready", "Cocktail", "Rowdy Rathore", "2 States", "Humshakals", "Tamasha", "Prem Ratan Dhan Payo", "Jab Harry Met Sejal", "Tubelight", "Judwaa 2", "Race 3", "Zero", "Thugs of Hindostan", "Good Newwz", "Laal Singh Chaddha", "Adipurush", "Dunki", "Crew", "Bad Newz", "Laapataa Ladies", "Sikandar", "Housefull 5", "Sitaare Zameen Par", "Dhurandhar: The Revenge", "Border 2", "Bhooth Bangla", "Dhamaal 4", "Welcome To The Jungle", "Mirzapur: The Movie", "Drishyam 3", "King", "Khalnayak", "Kabhi Haan Kabhi Naa", "Hero No. 1", "Yes Boss", "Pardes", "Satya", "Taal", "Sarfarosh", "Jo Jeeta Wohi Sikandar", "Rangeela", "Coolie No. 1", "Biwi No. 1", "Gangs of Wasseypur", "Ajab Prem Ki Ghazab Kahani", "Ae Dil Hai Mushkil", "No Entry", "Raja Hindustani")
        const val BLUFF_KEY = "Bluff"
        const val FRAME_KEY = "Frame"

        // Title first, then the film's big song; popular films from 2000 to 2026 (famous flops included), fewer from 1995-99, and only the biggest from 1990-94.
        val EMOJI = listOf(
            EmojiPuzzle("🗣️❤️😲", "Kaho Naa... Pyaar Hai", 2000, "Hrithik's debut, in a double role", "Ek Pal Ka Jeena"),
            EmojiPuzzle("🎻🌹🍂", "Mohabbatein", 2000, "Amitabh's strict Gurukul against SRK's music teacher", "Humko Humise Chura Lo"),
            EmojiPuzzle("💸📞👨👨👨", "Hera Pheri", 2000, "Akshay, Suniel, Paresh and a wrong-number ransom call", ""),
            EmojiPuzzle("😄😭👨‍👩‍👦‍👦", "Kabhi Khushi Kabhie Gham", 2001, "Amitabh's rich family; it's all about loving your parents", "Bole Chudiyan"),
            EmojiPuzzle("❤️🙏🚗", "Dil Chahta Hai", 2001, "Aamir, Saif, Akshaye: three friends and a trip to Goa", "Koi Kahe Kehta Rahe"),
            EmojiPuzzle("🌾💰🏏", "Lagaan", 2001, "Villagers play the British to cancel a tax", "Radha Kaise Na Jale"),
            EmojiPuzzle("🚛💥🇵🇰", "Gadar", 2001, "Sunny Deol pulls out a hand pump", "Udja Kale Kawa"),
            EmojiPuzzle("🥃💔🪔", "Devdas", 2002, "Bhansali's epic: SRK, Aishwarya, Madhuri", "Dola Re Dola"),
            EmojiPuzzle("🩺🤗😎", "Munna Bhai M.B.B.S.", 2003, "A gangster enrols in medical college; Sanjay Dutt", "Dekh Le"),
            EmojiPuzzle("👽🤝👦", "Koi... Mil Gaya", 2003, "Hrithik befriends an alien called Jadoo", "Idhar Chala Main Udhar Chala"),
            EmojiPuzzle("🌅❓🚫", "Kal Ho Naa Ho", 2003, "SRK with a bad heart in New York; Preity and Saif", "Maahi Ve"),
            EmojiPuzzle("🙋‍♂️✅🪖", "Main Hoon Na", 2004, "An army major goes undercover in a college; SRK", "Tumse Milke Dil Ka Hai Jo Haal"),
            EmojiPuzzle("🙋‍♂️🙋‍♀️✏️", "Hum Tum", 2004, "Saif the cartoonist keeps bumping into Rani", "Ladki Kyon"),
            EmojiPuzzle("💥🏍️", "Dhoom", 2004, "Abhishek and Uday chase John's bike gang", "Dhoom Machale"),
            EmojiPuzzle("🇮🇳❤️🇵🇰", "Veer-Zaara", 2004, "SRK, Preity and a love across the border", "Main Yahaan Hoon"),
            EmojiPuzzle("👦➕👧🎭💰", "Bunty Aur Babli", 2005, "Abhishek and Rani, con artists on the run", "Kajra Re"),
            EmojiPuzzle("🚫🚪", "No Entry", 2005, "Salman, Anil, Fardeen and a wife who can't find out", "Ishq Di Galli Vich No Entry"),
            EmojiPuzzle("🔥🌶️🧂", "Garam Masala", 2005, "Akshay and John juggle three air hostesses", "Falak Dekhun"),
            EmojiPuzzle("🙏👋", "Salaam Namaste", 2005, "Saif and Preity live together in Melbourne", "My Dil Goes Mmmm"),
            EmojiPuzzle("🎨🟡", "Rang De Basanti", 2006, "Students making a film on freedom fighters; Aamir", "Masti Ki Paathshala"),
            EmojiPuzzle("🦸‍♂️🎭🌲", "Krrish", 2006, "Hrithik gets a mask and a cape", "Dil Na Diya"),
            EmojiPuzzle("💥2️⃣🏍️", "Dhoom 2", 2006, "Hrithik the thief, Aishwarya his partner", "Crazy Kiya Re"),
            EmojiPuzzle("👓🌹🎙️", "Lage Raho Munna Bhai", 2006, "A gangster sees Gandhi and becomes a radio jockey", "Pal Pal Har Pal"),
            EmojiPuzzle("🕶️🌍🚓", "Don", 2006, "SRK; eleven countries' police are looking for him", "Main Hoon Don"),
            EmojiPuzzle("🤪🤐🏠", "Golmaal: Fun Unlimited", 2006, "Ajay's gang fools a blind couple; Tusshar can't speak", ""),
            EmojiPuzzle("🔁💸📞", "Phir Hera Pheri", 2006, "Raju, Shyam and Baburao get conned this time", ""),
            EmojiPuzzle("🕉️☮️🕉️", "Om Shanti Om", 2007, "A junior artist is reborn; SRK and Deepika's debut", "Dard-e-Disco"),
            EmojiPuzzle("🚆🤝", "Jab We Met", 2007, "Kareena's Geet meets Shahid on a train", "Mauja Hi Mauja"),
            EmojiPuzzle("🏑🇮🇳💪", "Chak De! India", 2007, "SRK coaches the women's hockey team", "Chak De India"),
            EmojiPuzzle("🤝🧔🧔", "Partner", 2007, "Salman is a love guru; Govinda needs his help", "Do You Wanna Partner"),
            EmojiPuzzle("🙏🚪😄", "Welcome", 2007, "Majnu bhai, Uday bhai and a very confused Akshay", "Uncha Lamba Kad"),
            EmojiPuzzle("⭐⭐🌍", "Taare Zameen Par", 2007, "Aamir the art teacher helps a dyslexic boy", "Bum Bum Bole"),
            EmojiPuzzle("🌀🏰👻", "Bhool Bhulaiyaa", 2007, "Akshay the psychiatrist, Vidya Balan's Manjulika", "Mere Dholna"),
            EmojiPuzzle("🏁🏎️", "Race", 2008, "Saif, Akshaye and Anil; everyone double-crosses", "Pehli Nazar Mein"),
            EmojiPuzzle("👳👑", "Singh Is Kinng", 2008, "Akshay accidentally becomes a gang boss", "Teri Ore"),
            EmojiPuzzle("📸🧠✊", "Ghajini", 2008, "Aamir's short-term memory, tattoos and Polaroids", "Guzarish"),
            EmojiPuzzle("🎸🤘", "Rock On!!", 2008, "Farhan's old band gets back together", "Socha Hai"),
            EmojiPuzzle("🙏✨👫", "Rab Ne Bana Di Jodi", 2008, "SRK's shy husband dances in disguise for Anushka", "Tujh Mein Rab Dikhta Hai"),
            EmojiPuzzle("👬🏠🌈", "Dostana", 2008, "Abhishek and John pretend to be a couple in Miami", "Desi Girl"),
            EmojiPuzzle("❤️🌞🌅", "Love Aaj Kal", 2009, "Saif and Deepika, love then and now", "Twist"),
            EmojiPuzzle("😲❤️📖", "Ajab Prem Ki Ghazab Kahani", 2009, "Ranbir and Katrina, a Goa-style Prem", "Tu Jaane Na"),
            EmojiPuzzle("😈😈", "Kaminey", 2009, "Shahid plays twins, one lisps, one stammers", "Dhan Te Nan"),
            EmojiPuzzle("🪧🔫😎", "Wanted", 2009, "Salman's undercover cop; once he commits", "Jalwa"),
            EmojiPuzzle("3️⃣🤪", "3 Idiots", 2009, "Aamir, Madhavan, Sharman at an engineering college", "All Izz Well"),
            EmojiPuzzle("🕶️👮😎", "Dabangg", 2010, "Salman's Chulbul Pandey and his sunglasses", "Munni Badnaam Hui"),
            EmojiPuzzle("3️⃣0️⃣🗡️👳", "Tees Maar Khan", 2010, "Akshay's con man robs a train, Farah Khan's famous flop", "Sheila Ki Jawani"),
            EmojiPuzzle("🙋‍♂️📛", "My Name Is Khan", 2010, "SRK sets out to meet the American President", "Sajda"),
            EmojiPuzzle("🥁🎺💒", "Band Baaja Baaraat", 2010, "Ranveer's debut: wedding planners with Anushka", "Ainvayi Ainvayi"),
            EmojiPuzzle("🏠🈵", "Housefull", 2010, "Akshay is the world's unluckiest man", "Papa Jag Jayega"),
            EmojiPuzzle("📜⏳🌆", "Once Upon a Time in Mumbaai", 2010, "Ajay and Emraan: Mumbai's gangsters in the 70s", "Pee Loon"),
            EmojiPuzzle("🤖🎮👨‍👦", "Ra.One", 2011, "SRK's video game villain escapes into real life", "Chammak Challo"),
            EmojiPuzzle("💪🕶️🛡️", "Bodyguard", 2011, "Salman protects Kareena, who keeps calling him", "Teri Meri"),
            EmojiPuzzle("✅⏱️😎", "Ready", 2011, "Salman and Asin; Character Dheela", "Dhinka Chika"),
            EmojiPuzzle("🦁👮", "Singham", 2011, "Ajay Devgn's roaring cop; Aata Majhi Satakli", "Aata Majhi Satakli"),
            EmojiPuzzle("🧬2️⃣🚫🚗", "Zindagi Na Milegi Dobara", 2011, "Three friends in Spain, each picks an adventure", "Senorita"),
            EmojiPuzzle("🏙️🤢💩", "Delhi Belly", 2011, "Imran Khan and friends; a stomach bug and smuggled diamonds", "Bhaag DK Bose"),
            EmojiPuzzle("🎸⭐", "Rockstar", 2011, "Ranbir and Nargis: a heartbroken musician called Jordan", "Sadda Haq"),
            EmojiPuzzle("🕶️2️⃣", "Don 2", 2011, "SRK robs a bank in Berlin", "Zara Dil Ko Thaam Lo"),
            EmojiPuzzle("🔥🛤️", "Agneepath", 2012, "Hrithik against Sanjay Dutt's bald Kancha", "Chikni Chameli"),
            EmojiPuzzle("1️⃣🐅", "Ek Tha Tiger", 2012, "Salman the RAW agent, Katrina from ISI", "Mashallah"),
            EmojiPuzzle("🍸", "Cocktail", 2012, "Saif, Deepika and Diana Penty in London", "Tumhi Ho Bandhu"),
            EmojiPuzzle("🍬🤐", "Barfi!", 2012, "Ranbir, deaf and mute, in Darjeeling; Priyanka", "Ala Barfi"),
            EmojiPuzzle("😤👮💃", "Rowdy Rathore", 2012, "Akshay in a double role, a small-time thief and a cop", "Chinta Ta Ta"),
            EmojiPuzzle("🎓🏆📅", "Student of the Year", 2012, "Alia, Varun and Sidharth's debut", "The Disco Song"),
            EmojiPuzzle("❤️⏳💣", "Jab Tak Hai Jaan", 2012, "SRK the bomb disposal man; Katrina and Anushka", "Challa"),
            EmojiPuzzle("💑2️⃣🎤", "Aashiqui 2", 2013, "Aditya Roy Kapur and Shraddha; a singer's downfall", "Tum Hi Ho"),
            EmojiPuzzle("🌟💃🏔️", "Yeh Jawaani Hai Deewani", 2013, "Ranbir and Deepika on a Manali trek", "Badtameez Dil"),
            EmojiPuzzle("🚂🌴", "Chennai Express", 2013, "SRK gets on the wrong train with Deepika", "Lungi Dance"),
            EmojiPuzzle("💥3️⃣🎩", "Dhoom 3", 2013, "Aamir the circus thief in Chicago", "Malang"),
            EmojiPuzzle("🔫🔫💃", "Goliyon Ki Raasleela Ram-Leela", 2013, "Ranveer and Deepika, feuding Gujarati families", "Nagada Sang Dhol"),
            EmojiPuzzle("👑✈️", "Queen", 2014, "Kangana goes on her honeymoon alone", "London Thumakda"),
            EmojiPuzzle("2️⃣🗺️💑", "2 States", 2014, "Arjun and Alia; a Punjabi and a Tamil family", "Locha-E-Ulfat"),
            EmojiPuzzle("🦵💥", "Kick", 2014, "Salman needs a thrill for everything", "Jumme Ki Raat"),
            EmojiPuzzle("👥👥👥🤡", "Humshakals", 2014, "Saif, Riteish and Ram Kapoor in triple roles; a loud flop", "Caller Tune"),
            EmojiPuzzle("🎉🎆📅", "Happy New Year", 2014, "SRK's team of misfits enters a dance contest in Dubai", "Manwa Laage"),
            EmojiPuzzle("👽📻", "PK", 2014, "Aamir as an alien who questions religion", "Tharki Chokro"),
            EmojiPuzzle("🐒🙏🇵🇰", "Bajrangi Bhaijaan", 2015, "Salman takes a mute girl home across the border", "Selfie Le Le Re"),
            EmojiPuzzle("❤️😎🚗", "Dilwale", 2015, "SRK and Kajol reunite; Varun and Kriti too", "Gerua"),
            EmojiPuzzle("❤️💎💰", "Prem Ratan Dhan Payo", 2015, "Salman doubles for a prince; Sonam", "Prem Ratan Dhan Payo"),
            EmojiPuzzle("🎭🇫🇷", "Tamasha", 2015, "Ranbir and Deepika pretend to be strangers in Corsica", "Matargashti"),
            EmojiPuzzle("🤼👑", "Sultan", 2016, "Salman the Haryanvi wrestler; Anushka", "Baby Ko Bass Pasand Hai"),
            EmojiPuzzle("🤼‍♀️🥇", "Dangal", 2016, "Aamir trains his daughters to wrestle", "Haanikaarak Bapu"),
            EmojiPuzzle("❤️😣", "Ae Dil Hai Mushkil", 2016, "Ranbir, Anushka, Aishwarya; one-sided love", "Channa Mereya"),
            EmojiPuzzle("💰🕶️🥃", "Raees", 2017, "SRK as a Gujarat bootlegger", "Laila Main Laila"),
            EmojiPuzzle("👨💍👩✈️", "Jab Harry Met Sejal", 2017, "SRK and Anushka search Europe for a lost ring", "Radha"),
            EmojiPuzzle("👯2️⃣", "Judwaa 2", 2017, "Varun's twins, one raised in London", "Chalti Hai Kya 9 Se 12"),
            EmojiPuzzle("💡📏", "Tubelight", 2017, "Salman's slow-witted brother, a big flop", "Radio"),
            EmojiPuzzle("👑🔥🪞", "Padmaavat", 2018, "Deepika, Shahid, and Ranveer's Khilji", "Ghoomar"),
            EmojiPuzzle("🎬🚬👨‍👦", "Sanju", 2018, "Ranbir plays Sanjay Dutt", "Kar Har Maidaan Fateh"),
            EmojiPuzzle("🏁3️⃣", "Race 3", 2018, "Salman, Bobby Deol, Jacqueline; our business is our business", "Selfish"),
            EmojiPuzzle("🏴‍☠️🇮🇳", "Thugs of Hindostan", 2018, "Aamir and Amitabh as pirates, a huge flop", "Manzoor-e-Khuda"),
            EmojiPuzzle("0️⃣", "Zero", 2018, "SRK plays a short man; Anushka and Katrina", "Mere Naam Tu"),
            EmojiPuzzle("👻🚺", "Stree", 2018, "Rajkummar Rao in Chanderi, where a ghost takes men", "Kamariya"),
            EmojiPuzzle("🩺🥃💔", "Kabir Singh", 2019, "Shahid's angry surgeon falls apart", "Bekhayali"),
            EmojiPuzzle("🎤🏘️", "Gully Boy", 2019, "Ranveer raps his way out of Dharavi", "Apna Time Aayega"),
            EmojiPuzzle("⚔️", "War", 2019, "Hrithik against Tiger Shroff", "Ghungroo"),
            EmojiPuzzle("🤰🗞️", "Good Newwz", 2019, "Akshay, Kareena, Diljit, Kiara; an IVF mix-up", "Sauda Khara Khara"),
            EmojiPuzzle("🔱🔥🧔", "Brahmastra", 2022, "Ranbir controls fire; Alia and Amitabh", "Kesariya"),
            EmojiPuzzle("🕶️🪖💣", "Pathaan", 2023, "SRK's comeback as a spy; Deepika and John", "Besharam Rang"),
            EmojiPuzzle("🪖👮👨‍👦", "Jawan", 2023, "SRK in a double role; Nayanthara and Vijay Sethupathi", "Chaleya"),
            EmojiPuzzle("🐯🪓🩸", "Animal", 2023, "Ranbir, obsessed with his father; Bobby Deol's silent villain", "Arjan Vailly"),
            EmojiPuzzle("🚛2️⃣🇵🇰", "Gadar 2", 2023, "Tara Singh returns for his son", "Main Nikla Gaddi Leke"),
            EmojiPuzzle("👻2️⃣", "Stree 2", 2024, "Rajkummar Rao and Shraddha, now against Sarkata", "Aaj Ki Raat"),
            EmojiPuzzle("⚔️🏰🚩", "Tanhaji", 2020, "Ajay Devgn, Maratha warrior; Saif as the villain", "Shankara Re Shankara"),
            EmojiPuzzle("☀️👮", "Sooryavanshi", 2021, "Akshay's cop joins Singham and Simmba", "Tip Tip Barsa Paani"),
            EmojiPuzzle("🤍🚬👸🏻", "Gangubai Kathiawadi", 2022, "Alia in white saris, ruling Kamathipura", "Dholida"),
            EmojiPuzzle("🌀🏰2️⃣", "Bhool Bhulaiyaa 2", 2022, "Kartik's Rooh Baba, Tabu's Manjulika", "Bhool Bhulaiyaa Title Track"),
            EmojiPuzzle("🏃‍♂️🪶", "Laal Singh Chaddha", 2022, "Aamir's Hindi Forrest Gump, a big flop", "Tere Hawaale"),
            EmojiPuzzle("🏹🐒", "Adipurush", 2023, "Prabhas's Ramayana, that infamous VFX", "Ram Siya Ram"),
            EmojiPuzzle("👦➕👧📖", "Rocky Aur Rani Kii Prem Kahaani", 2023, "Ranveer and Alia swap families", "Tum Kya Mile"),
            EmojiPuzzle("🚶‍♂️✈️🇬🇧", "Dunki", 2023, "SRK and friends take the illegal route to London", "Lutt Putt Gaya"),
            EmojiPuzzle("🐅3️⃣", "Tiger 3", 2023, "Salman and Katrina; SRK drops in", "Leke Prabhu Ka Naam"),
            EmojiPuzzle("1️⃣2️⃣❌📚", "12th Fail", 2023, "Vikrant Massey cracks the UPSC exam", ""),
            EmojiPuzzle("✈️🔥🇮🇳", "Fighter", 2024, "Hrithik and Deepika as Air Force pilots", "Sher Khul Gaye"),
            EmojiPuzzle("✈️👩‍✈️👩‍✈️👩‍✈️💰", "Crew", 2024, "Tabu, Kareena, Kriti as air hostesses smuggling gold", "Naina"),
            EmojiPuzzle("📰😱👶", "Bad Newz", 2024, "Vicky Kaushal's dance step; Triptii, Ammy Virk", "Tauba Tauba"),
            EmojiPuzzle("👰👰🚂❓", "Laapataa Ladies", 2024, "Two brides get swapped on a train", ""),
            EmojiPuzzle("🌀🏰3️⃣", "Bhool Bhulaiyaa 3", 2024, "Kartik with Vidya and Madhuri, two Manjulikas", "Ami Je Tomar 3.0"),
            EmojiPuzzle("🦁👶⚔️", "Chhaava", 2025, "Vicky Kaushal as Sambhaji Maharaj", "Jaane Tu"),
            EmojiPuzzle("🪐🎤💔", "Saiyaara", 2025, "Ahaan Panday's debut with Aneet Padda", "Saiyaara"),
            EmojiPuzzle("🎖️🕵️🇵🇰", "Dhurandhar", 2025, "Ranveer undercover in Lyari; Akshaye Khanna's dance", "Shararat"),
            EmojiPuzzle("👑🌍😎", "Sikandar", 2025, "Salman's Eid release and big flop; Rashmika", "Zohra Jabeen"),
            EmojiPuzzle("⚔️2️⃣", "War 2", 2025, "Hrithik, Jr NTR and Kiara, an expensive flop", "Aavan Jaavan"),
            EmojiPuzzle("🏠🈵5️⃣", "Housefull 5", 2025, "Akshay and a murder mystery on a cruise", "Laal Pari"),
            EmojiPuzzle("⭐🌍🏀", "Sitaare Zameen Par", 2025, "Aamir coaches a basketball team", ""),
            EmojiPuzzle("🕵️2️⃣🔥", "Dhurandhar: The Revenge", 2026, "Ranveer returns; the biggest film of the year", "Aari Aari"),
            EmojiPuzzle("🪖🇮🇳2️⃣", "Border 2", 2026, "Sunny Deol back in uniform; Varun and Diljit", "Ghar Kab Aaoge"),
            EmojiPuzzle("👻🏚️", "Bhooth Bangla", 2026, "Akshay and Priyadarshan reunite for horror comedy", ""),
            EmojiPuzzle("🤪4️⃣💰", "Dhamaal 4", 2026, "Ajay, Riteish, Arshad and more treasure chasing", ""),
            EmojiPuzzle("🙏🌴🐅", "Welcome To The Jungle", 2026, "Akshay leads a huge comedy cast", ""),
            EmojiPuzzle("🔫🪑🩸", "Mirzapur: The Movie", 2026, "Kaleen Bhaiya and Guddu on the big screen", ""),
            EmojiPuzzle("👣🕵️", "Drishyam 3", 2026, "Ajay Devgn's Vijay Salgaonkar, one more time", ""),
            EmojiPuzzle("👑🦁", "King", 2026, "SRK with daughter Suhana and Deepika; out on 24 December", "Tu Mil Jaaye"),
            EmojiPuzzle("🕶️🏢👧", "Baazigar", 1993, "SRK pushes Shilpa off a roof", "Yeh Kaali Kaali Aankhen"),
            EmojiPuzzle("🐶💌💒", "Hum Aapke Hain Koun", 1994, "Salman and Madhuri, a family wedding, Tuffy the dog", "Didi Tera Devar Deewana"),
            EmojiPuzzle("👦👦💰🐔", "Andaz Apna Apna", 1994, "Aamir and Salman chase an heiress; Crime Master Gogo", "Elo Ji Sanam Hum Aa Gaye"),
            EmojiPuzzle("🌻🚂👰🏻", "Dilwale Dulhania Le Jayenge", 1995, "SRK and Kajol: a Europe trip, then Punjab's mustard fields", "Tujhe Dekha To"),
            EmojiPuzzle("👦👦♻️👩‍🦳", "Karan Arjun", 1995, "Two brothers reborn to avenge their mother", "Bhangra Paale"),
            EmojiPuzzle("❤️🤪💃", "Dil To Pagal Hai", 1997, "SRK, Madhuri, Karisma; a dance troupe", "Are Re Are"),
            EmojiPuzzle("🪖🇮🇳🇵🇰⚔️", "Border", 1997, "Sunny Deol and the battle of Longewala", "Sandese Aate Hain"),
            EmojiPuzzle("🏀💌👧", "Kuch Kuch Hota Hai", 1998, "SRK, Kajol, Rani: a late mother's letters and a summer camp", "Koi Mil Gaya"),
            EmojiPuzzle("👨‍👩‍👧‍👦👨‍👩‍👧‍👦❤️", "Hum Saath Saath Hain", 1999, "Salman, Saif, Mohnish; one very big happy family", "Sunoji Dulhan"),
            EmojiPuzzle("🚲🏆🏫", "Jo Jeeta Wohi Sikandar", 1992, "Aamir's cycle race between rival colleges", "Pehla Nasha"),
            EmojiPuzzle("😈🦸🔫", "Khalnayak", 1993, "Sanjay's Ballu; Madhuri goes undercover", "Choli Ke Peeche"),
            EmojiPuzzle("✅❌🎸", "Kabhi Haan Kabhi Naa", 1994, "SRK loses in love and in his band, in Goa", "Deewana Dil Deewana"),
            EmojiPuzzle("🌈👧🎬", "Rangeela", 1995, "Urmila, Aamir's Munna and Jackie the star", "Tanha Tanha"),
            EmojiPuzzle("🚉1️⃣🧳", "Coolie No. 1", 1995, "Govinda the railway porter, Karisma", "Main To Raste Se Ja Raha Tha"),
            EmojiPuzzle("👑🇮🇳❤️", "Raja Hindustani", 1996, "Aamir the taxi driver loves rich Karisma", "Pardesi Pardesi"),
            EmojiPuzzle("🦸1️⃣👨‍👩‍👦", "Hero No. 1", 1997, "Govinda as a servant in his girl's home", "Sona Kitna Sona Hai"),
            EmojiPuzzle("🇺🇸💍🇮🇳", "Pardes", 1997, "SRK, Mahima; an NRI groom in America", "I Love My India"),
            EmojiPuzzle("✅💼😎", "Yes Boss", 1997, "SRK fixes life for his boss; Juhi", "Main Koi Aisa Geet Gaoon"),
            EmojiPuzzle("❤️🚂💃", "Dil Se..", 1998, "SRK and Manisha; that song on a train", "Chaiyya Chaiyya"),
            EmojiPuzzle("🔫🌆🕺", "Satya", 1998, "Bheeku Mhatre and Mumbai's underworld", "Sapne Mein Milti Hai"),
            EmojiPuzzle("👰1️⃣👩", "Biwi No. 1", 1999, "Salman, Karisma and Sushmita", "Chunnari Chunnari"),
            EmojiPuzzle("🥁🎶🎶", "Taal", 1999, "Aishwarya, Akshaye, Anil; A. R. Rahman's music", "Taal Se Taal Mila"),
            EmojiPuzzle("☀️📺👴", "Sooryavansham", 1999, "Amitabh double role; always on Set Max", "Kore Kore Sapne"),
            EmojiPuzzle("🪖🕵️🇮🇳", "Sarfarosh", 1999, "Aamir the ACP against Naseeruddin's ghazal singer", "Hoshwalon Ko Khabar Kya"),
        )

        // The line, the film.
        // Filmi Bluff: blanked fact, the true answer, two lies.
        val BLUFFS = listOf(
            listOf("In Hera Pheri, Babu Bhaiya's full name is Baburao Ganpatrao ____", "Apte", "Kulkarni", "Bhaiyya"),
            listOf("In 3 Idiots, Chatur Ramalingam's nickname is ____", "Silencer", "Xerox", "Rattu Tota"),
            listOf("In Dhamaal, the treasure is hidden under a big ____", "W", "Banyan tree", "Statue of Gandhi"),
            listOf("In Dil Dhadakne Do, the family dog Pluto is voiced by ____", "Aamir Khan", "Boman Irani", "Karan Johar"),
            listOf("In Dabangg, Chulbul Pandey's brother goes by the nickname ____", "Makkhi", "Chhotu", "Tinku"),
            listOf("In Rockstar, Jordan's real name is ____", "Janardhan Jakhar", "Jatin Jaiswal", "Jagdish Joshi"),
            listOf("In Barfi!, the name Barfi comes from how he says his real name, ____", "Murphy", "Bernard", "Barry"),
            listOf("In Bajrangi Bhaijaan, the little girl he calls Munni is really named ____", "Shahida", "Ayesha", "Noor"),
            listOf("In Ek Tha Tiger, Tiger's real name is ____", "Avinash Singh Rathore", "Vikram Singh Shekhawat", "Arjun Pratap Rana"),
            listOf("The band in Rock On!! is called ____", "Magik", "Bandra Boys", "Parikrama"),
            listOf("Hum Aapke Hain Koun's beloved dog is named ____", "Tuffy", "Bruno", "Moti"),
            listOf("Stree is set in the town of ____", "Chanderi", "Bhopal", "Mandu"),
            listOf("In Animal, Bobby Deol's Abrar never ____", "Speaks", "Blinks", "Wears shoes"),
            listOf("In Chennai Express, Rahul is on his way to scatter his grandfather's ashes in ____", "Rameswaram", "Goa", "Haridwar"),
            listOf("In Queen, Rani goes on her honeymoon alone to Paris and ____", "Amsterdam", "Vienna", "Bangkok"),
            listOf("In Tamasha, Ved and Tara meet on holiday in ____", "Corsica", "Santorini", "Bali"),
            listOf("In Dostana, Sam and Kunal pretend to be a couple in ____", "Miami", "Las Vegas", "Sydney"),
            listOf("In Drishyam, the whole alibi depends on a satsang on ____", "2 October", "15 August", "31 December"),
            listOf("Hum Dil De Chuke Sanam is set in Italy but was really shot in ____", "Hungary", "Switzerland", "Ooty"),
            listOf("In Koi... Mil Gaya, Jadoo the alien gets his powers from ____", "Sunlight", "Ghee", "Music"),
            listOf("In Jab We Met, Geet's home town is ____", "Bhatinda", "Amritsar", "Patiala"),
            listOf("In Yeh Jawaani Hai Deewani, Bunny's nickname for Naina is ____", "Chashmish", "Padhaku", "Topper"),
            listOf("In Mohabbatein, Amitabh's strict school is called ____", "Gurukul", "Doon Valley", "St. Teresa's"),
            listOf("In Lagaan, Bhuvan's village is called ____", "Champaner", "Rampur", "Sultanpur"),
            listOf("In 3 Idiots, the college goes by the short name ____", "ICE", "VIRUS", "IIT-B"),
            listOf("In PK, Aamir's alien is searching for his lost ____", "Remote control", "Spaceship key", "Mobile charger"),
            listOf("In Bhool Bhulaiyaa 2, Kartik Aaryan poses as the ghost expert ____", "Rooh Baba", "Tantrik Tinku", "Bhoot Baba"),
            listOf("Ra.One stands for Random Access ____", "Version One", "Robot Android", "Rogue Online"),
            listOf("Shah Rukh Khan's house Mannat used to be called ____", "Villa Vienna", "Sea Palace", "Shanti Niwas"),
            listOf("The title Dilwale Dulhania Le Jayenge was suggested by ____", "Kirron Kher", "Yash Chopra", "Lata Mangeshkar"),
            listOf("Amitabh Bachchan was once turned down for his voice by ____", "All India Radio", "Doordarshan", "HMV"),
            listOf("Before films, Akshay Kumar worked in Bangkok as a ____", "Chef", "Tour guide", "Boxing referee"),
            listOf("Before acting, Boman Irani ran his family's ____ shop", "Wafer", "Sari", "Cycle repair"),
            listOf("Before films, Nawazuddin Siddiqui worked in Baroda as a ____", "Chemist", "Bus conductor", "Tailor"),
            listOf("Young Shahid Kapoor can be spotted as a background dancer in ____", "Taal", "Kuch Kuch Hota Hai", "Hum Saath-Saath Hain"),
            listOf("Ranbir Kapoor was an assistant director on ____", "Black", "Devdas", "Lagaan"),
            listOf("Aishwarya Rai's very first film was the Tamil film ____", "Iruvar", "Roja", "Jeans"),
            listOf("Katrina Kaif's first film was ____", "Boom", "Maine Pyaar Kyun Kiya", "Sarkar"),
            listOf("Shah Rukh Khan's first TV show was ____", "Fauji", "Nukkad", "Hum Log"),
            listOf("As a child, Aamir Khan appeared in ____", "Yaadon Ki Baaraat", "Mr. India", "Masoom"),
            listOf("Hrithik Roshan has an extra ____ on his right hand", "Thumb", "Finger", "Knuckle"),
            listOf("Before Bollywood, Arshad Warsi sold ____ door to door", "Cosmetics", "Encyclopaedias", "Pressure cookers"),
            listOf("Before Vicky Donor, Ayushmann Khurrana won ____", "MTV Roadies", "Indian Idol", "Bournvita Quiz Contest"),
        )

        // Song, film.
        val HUM = listOf(
            listOf("Tum Hi Ho", "Aashiqui 2"),
            listOf("Kajra Re", "Bunty Aur Babli"),
            listOf("Munni Badnaam Hui", "Dabangg"),
            listOf("Sheila Ki Jawani", "Tees Maar Khan"),
            listOf("Chammak Challo", "Ra.One"),
            listOf("Lungi Dance", "Chennai Express"),
            listOf("Balam Pichkari", "Yeh Jawaani Hai Deewani"),
            listOf("Badtameez Dil", "Yeh Jawaani Hai Deewani"),
            listOf("Kabira", "Yeh Jawaani Hai Deewani"),
            listOf("Ilahi", "Yeh Jawaani Hai Deewani"),
            listOf("Desi Girl", "Dostana"),
            listOf("Chaiyya Chaiyya", "Dil Se.."),
            listOf("Dola Re Dola", "Devdas"),
            listOf("Dard-e-Disco", "Om Shanti Om"),
            listOf("Crazy Kiya Re", "Dhoom 2"),
            listOf("Dhoom Machale", "Dhoom"),
            listOf("Chikni Chameli", "Agneepath"),
            listOf("Ishq Wala Love", "Student of the Year"),
            listOf("Gerua", "Dilwale"),
            listOf("Channa Mereya", "Ae Dil Hai Mushkil"),
            listOf("Kesariya", "Brahmastra"),
            listOf("Chaleya", "Jawan"),
            listOf("Besharam Rang", "Pathaan"),
            listOf("Jhoome Jo Pathaan", "Pathaan"),
            listOf("Tauba Tauba", "Bad Newz"),
            listOf("Apna Time Aayega", "Gully Boy"),
            listOf("Ghoomar", "Padmaavat"),
            listOf("Khalibali", "Padmaavat"),
            listOf("Selfie Le Le Re", "Bajrangi Bhaijaan"),
            listOf("Kala Chashma", "Baar Baar Dekho"),
            listOf("Nagada Sang Dhol", "Goliyon Ki Raasleela Ram-Leela"),
            listOf("Tattad Tattad", "Goliyon Ki Raasleela Ram-Leela"),
            listOf("Bole Chudiyan", "Kabhi Khushi Kabhie Gham"),
            listOf("Mauja Hi Mauja", "Jab We Met"),
            listOf("Tum Se Hi", "Jab We Met"),
            listOf("Tujh Mein Rab Dikhta Hai", "Rab Ne Bana Di Jodi"),
            listOf("Pehli Nazar Mein", "Race"),
            listOf("Jumme Ki Raat", "Kick"),
            listOf("Tere Mast Mast Do Nain", "Dabangg"),
            listOf("Senorita", "Zindagi Na Milegi Dobara"),
            listOf("Sadda Haq", "Rockstar"),
            listOf("Uncha Lamba Kad", "Welcome"),
            listOf("Kal Ho Naa Ho", "Kal Ho Naa Ho"),
            listOf("Maahi Ve", "Kal Ho Naa Ho"),
            listOf("Kuch Kuch Hota Hai", "Kuch Kuch Hota Hai"),
            listOf("Koi Mil Gaya", "Kuch Kuch Hota Hai"),
            listOf("Tujhe Dekha To", "Dilwale Dulhania Le Jayenge"),
            listOf("All Izz Well", "3 Idiots"),
            listOf("Zoobi Doobi", "3 Idiots"),
            listOf("Chak De India", "Chak De! India"),
            listOf("Radha Kaise Na Jale", "Lagaan"),
            listOf("Ghanan Ghanan", "Lagaan"),
            listOf("Dil Chahta Hai", "Dil Chahta Hai"),
            listOf("Koi Kahe Kehta Rahe", "Dil Chahta Hai"),
            listOf("Main Hoon Na", "Main Hoon Na"),
            listOf("Tere Naam", "Tere Naam"),
            listOf("Ami Je Tomar", "Bhool Bhulaiyaa"),
            listOf("Ainvayi Ainvayi", "Band Baaja Baaraat"),
            listOf("Gallan Goodiyaan", "Dil Dhadakne Do"),
            listOf("London Thumakda", "Queen"),
            listOf("Malhari", "Bajirao Mastani"),
            listOf("Pinga", "Bajirao Mastani"),
            listOf("Malang", "Dhoom 3"),
            listOf("Zingaat", "Dhadak"),
            listOf("What Jhumka?", "Rocky Aur Rani Kii Prem Kahaani"),
            listOf("Apna Bana Le", "Bhediya"),
            listOf("Tere Vaaste", "Zara Hatke Zara Bachke"),
            listOf("Aaj Ki Raat", "Stree 2"),
        )

        val DIALOGUES = listOf(
            listOf("Bade bade deshon mein aisi chhoti chhoti baatein hoti rehti hain", "Dilwale Dulhania Le Jayenge"),
            listOf("Don ko pakadna mushkil hi nahi, namumkin hai", "Don"),
            listOf("Picture abhi baaki hai mere dost", "Om Shanti Om"),
            listOf("Ek chutki sindoor ki keemat tum kya jaano", "Om Shanti Om"),
            listOf("Keh diya na, bas keh diya", "Kabhi Khushi Kabhie Gham"),
            listOf("Pyaar dosti hai", "Kuch Kuch Hota Hai"),
            listOf("Parampara, pratishtha, anushasan", "Mohabbatein"),
            listOf("Main apni favourite hoon", "Jab We Met"),
            listOf("Kaamyab hone ke liye nahi, kaabil hone ke liye padho", "3 Idiots"),
            listOf("Thappad se darr nahi lagta sahab, pyaar se lagta hai", "Dabangg"),
            listOf("Aata majhi satakli", "Singham"),
            listOf("Ek baar jo maine commitment kar di, phir main khud ki bhi nahi sunta", "Wanted"),
            listOf("Main dil mein aata hoon, samajh mein nahi", "Kick"),
            listOf("Hindustan zindabad tha, zindabad hai, aur zindabad rahega", "Gadar"),
            listOf("Yeh Baburao ka style hai", "Hera Pheri"),
            listOf("25 din mein paisa double", "Hera Pheri"),
            listOf("Utha le re baba", "Phir Hera Pheri"),
            listOf("Control Uday, control", "Welcome"),
            listOf("Tension nahi lene ka", "Munna Bhai M.B.B.S."),
            listOf("Jaadu ki jhappi", "Munna Bhai M.B.B.S."),
            listOf("Main udna chahta hoon, daudna chahta hoon, bas rukna nahi chahta", "Yeh Jawaani Hai Deewani"),
            listOf("Sattar minute hai tumhare paas", "Chak De! India"),
            listOf("Vijay Deenanath Chauhan, poora naam", "Agneepath"),
            listOf("Kabhi kabhi jeetne ke liye kuch haarna bhi padta hai", "Baazigar"),
            listOf("Jo main bolta hoon woh main karta hoon", "Rowdy Rathore"),
            listOf("Baniye ka dimaag aur miyan bhai ki daring", "Raees"),
            listOf("Bete ko haath lagane se pehle baap se baat kar", "Jawan"),
            listOf("Party Pathaan ke ghar pe rakhoge toh Pathaan toh aayega hi", "Pathaan"),
            listOf("O stree, kal aana", "Stree"),
            listOf("Mhaari chhoriyan chhoron se kam hain ke?", "Dangal"),
            listOf("Keh ke lenge", "Gangs of Wasseypur"),
            listOf("Beta, tumse na ho payega", "Gangs of Wasseypur"),
            listOf("Aap purush hi nahi, mahapurush hain", "Andaz Apna Apna"),
            listOf("Aankhen nikal ke gotiyan khelta hoon", "Andaz Apna Apna"),
            listOf("Babuji ne kaha gaon chhod do", "Devdas"),
            listOf("My name is Khan, and I am not a terrorist", "My Name Is Khan"),
            listOf("Wrong number", "PK"),
            listOf("Mere Karan Arjun aayenge", "Karan Arjun"),
        )

        // The main cast, the film.
        val CAST = listOf(
            listOf("Aamir Khan, R. Madhavan, Sharman Joshi", "3 Idiots"),
            listOf("Aamir Khan, Saif Ali Khan, Akshaye Khanna", "Dil Chahta Hai"),
            listOf("Hrithik Roshan, Farhan Akhtar, Abhay Deol", "Zindagi Na Milegi Dobara"),
            listOf("Shah Rukh Khan, Kajol, Rani Mukerji", "Kuch Kuch Hota Hai"),
            listOf("Amitabh Bachchan, Jaya Bachchan, Shah Rukh Khan, Kajol, Hrithik, Kareena", "Kabhi Khushi Kabhie Gham"),
            listOf("Amitabh Bachchan, Shah Rukh Khan, Aishwarya Rai", "Mohabbatein"),
            listOf("Shah Rukh Khan, Saif Ali Khan, Preity Zinta", "Kal Ho Naa Ho"),
            listOf("Akshay Kumar, Suniel Shetty, Paresh Rawal", "Hera Pheri"),
            listOf("Akshay Kumar, Katrina Kaif, Anil Kapoor, Nana Patekar", "Welcome"),
            listOf("Salman Khan, Anil Kapoor, Fardeen Khan", "No Entry"),
            listOf("Akshay Kumar, John Abraham, Rimi Sen", "Garam Masala"),
            listOf("Abhishek Bachchan, John Abraham, Priyanka Chopra", "Dostana"),
            listOf("Hrithik Roshan, Abhishek Bachchan, Aishwarya Rai", "Dhoom 2"),
            listOf("Abhishek Bachchan, Rani Mukerji, Amitabh Bachchan", "Bunty Aur Babli"),
            listOf("Saif Ali Khan, Akshaye Khanna, Anil Kapoor, Katrina Kaif", "Race"),
            listOf("Akshay Kumar, Katrina Kaif, Om Puri, Sonu Sood", "Singh Is Kinng"),
            listOf("Shahid Kapoor, Kareena Kapoor", "Jab We Met"),
            listOf("Ranbir Kapoor, Deepika Padukone, Aditya Roy Kapur, Kalki", "Yeh Jawaani Hai Deewani"),
            listOf("Ranbir Kapoor, Priyanka Chopra, Ileana D'Cruz", "Barfi!"),
            listOf("Ranbir Kapoor, Anushka Sharma, Aishwarya Rai", "Ae Dil Hai Mushkil"),
            listOf("Saif Ali Khan, Deepika Padukone, Diana Penty", "Cocktail"),
            listOf("Salman Khan, Sonakshi Sinha, Sonu Sood", "Dabangg"),
            listOf("Salman Khan, Kareena Kapoor, Nawazuddin Siddiqui", "Bajrangi Bhaijaan"),
            listOf("Shah Rukh Khan, Deepika Padukone, Shreyas Talpade, Arjun Rampal", "Om Shanti Om"),
            listOf("Shah Rukh Khan, Juhi Chawla, Aditya Pancholi", "Yes Boss"),
            listOf("Shah Rukh Khan, Deepika Padukone, Sathyaraj", "Chennai Express"),
            listOf("Shah Rukh Khan, Deepika Padukone, John Abraham", "Pathaan"),
            listOf("Shah Rukh Khan, Nayanthara, Vijay Sethupathi", "Jawan"),
            listOf("Aamir Khan, Anushka Sharma, Sushant Singh Rajput", "PK"),
            listOf("Aamir Khan, Katrina Kaif, Abhishek Bachchan, Uday Chopra", "Dhoom 3"),
            listOf("Aamir Khan, Amitabh Bachchan, Katrina Kaif, Fatima Sana Shaikh", "Thugs of Hindostan"),
            listOf("Akshay Kumar, Katrina Kaif, Akshaye Khanna", "Tees Maar Khan"),
            listOf("Ranveer Singh, Deepika Padukone, Shahid Kapoor", "Padmaavat"),
            listOf("Ranveer Singh, Alia Bhatt, Siddhant Chaturvedi", "Gully Boy"),
            listOf("Ranbir Kapoor, Bobby Deol, Rashmika Mandanna, Anil Kapoor", "Animal"),
            listOf("Ranbir Kapoor, Alia Bhatt, Amitabh Bachchan, Nagarjuna", "Brahmastra"),
            listOf("Hrithik Roshan, Tiger Shroff, Vaani Kapoor", "War"),
            listOf("Akshay Kumar, Vidya Balan, Shiney Ahuja, Paresh Rawal", "Bhool Bhulaiyaa"),
            listOf("Vicky Kaushal, Rashmika Mandanna, Akshaye Khanna", "Chhaava"),
            listOf("Ranveer Singh, Akshaye Khanna, Sanjay Dutt, R. Madhavan", "Dhurandhar"),
            listOf("Shah Rukh Khan, Suhana Khan, Deepika Padukone", "King"),
        )

        // Plot, badly explained, then the film.
        // Plot, film, how hard, and a joke of our own for the reveal.
        val PLOTS = listOf(
            listOf("Two grown men crash a funeral, steal the ashes and chase someone across India over a degree he didn't technically earn", "3 Idiots", "Easy", "All is well, except for everyone's engineering degree."),
            listOf("A man fails his exams, ruins a girl's Europe trip, then moves in with her family and does chores until her dad gives up", "Dilwale Dulhania Le Jayenge", "Easy", "Decades in one Mumbai theatre, and the mustard fields still haven't been harvested."),
            listOf("A dying neighbour fixes an entire family's problems in New York, then hands the woman he loves to his best friend using a fake diary", "Kal Ho Naa Ho", "Medium", "The only film where a heart condition doubles as a wingman."),
            listOf("A dead mother schedules eight years of letters so her child can set up the husband with his old friend. Nobody finds this strange", "Kuch Kuch Hota Hai", "Medium", "The eight-year-old has better relationship instincts than every adult in it."),
            listOf("A billionaire disowns his son over a wife from the wrong neighbourhood. Ten years later, everyone cries about it in London", "Kabhi Khushi Kabhie Gham", "Easy", "A London school sings the Indian anthem and nobody asks a single question."),
            listOf("India's strictest principal is defeated by a violin teacher, three couples and his own dead daughter", "Mohabbatein", "Medium", "Gurukul's rules: no love, no fun, and absolutely no noticing the ghost."),
            listOf("A depressed millionaire gets off a train to rescue a very chatty stranger, and Punjabi food fixes him", "Jab We Met", "Medium", "Nobody in history has been this cheerful about missing a train."),
            listOf("Three friends have a midlife crisis in Spain, treated with skydiving, tomatoes and bad poetry", "Zindagi Na Milegi Dobara", "Medium", "A pre-wedding trip with a higher death risk than most war films."),
            listOf("A man with a 15-minute memory forgets his own name but never misses the gym, a chest tattoo or his revenge schedule", "Ghajini", "Easy", "He tattoos his to-do list on his body and still never forgets leg day."),
            listOf("A naked alien lands in Rajasthan, loses his remote and accidentally starts a national debate about God", "PK", "Easy", "Lost: one remote. Found: every awkward question in India."),
            listOf("Dumped before her wedding, a girl goes on the honeymoon alone, gets drunk in Paris and shares a room with three men. Best trip of her life", "Queen", "Medium", "The honeymoon improved the moment the groom cancelled."),
            listOf("An old Bengali man's constipation gets its own road trip, its own taxi driver and a two-hour storyline", "Piku", "Medium", "India's only road movie steered entirely by digestion."),
            listOf("A man too honest to sneak across a border asks the guards for permission, then walks a lost child to Pakistan anyway", "Bajrangi Bhaijaan", "Easy", "Border control was no match for one man's total inability to lie."),
            listOf("An angry dad wanted a son to win him gold. He cuts his daughters' hair and feeds them chicken instead", "Dangal", "Easy", "The parenting plan: chicken, short haircuts and a national gold medal."),
            listOf("A cable TV guy beats an entire police force because he has watched too many movies. Do not invite him to a satsang", "Drishyam", "Medium", "Nobody will ever forget 2 October, which was the whole idea."),
            listOf("A pianist pretends to be blind, then actually goes blind, and that's somehow the least of his problems. A rabbit is involved", "Andhadhun", "Medium", "Everyone in this film is lying, possibly including the piano."),
            listOf("The men of a small town start wearing saris for safety, because the ghost only takes men and respects written requests", "Stree", "Medium", "The only ghost in Bollywood with better manners than the men she takes."),
            listOf("A headless man who hates modern women hunts a small town's girls, so the men ask their old ghost for a favour", "Stree 2", "Hard", "Sequel logic: the scary woman is now the town's security guard."),
            listOf("A man shaves his moustache and dances with his own wife. She doesn't recognise him for most of the film", "Rab Ne Bana Di Jodi", "Medium", "Take off the moustache and your own wife can't recognise you. Sure."),
            listOf("Two straight men pretend to be a couple to get a flat in Miami, and one mother takes it very emotionally", "Dostana", "Medium", "Two men lied for a flat, which in Miami is still cheaper than rent."),
            listOf("A con man convinces an entire village they're extras in a film so that he can rob a train", "Tees Maar Khan", "Medium", "It flopped so hard it became a classic. One song paid all the bills."),
            listOf("A video game villain climbs out of the screen to bully a child, so the hero has to be downloaded. A famous robot briefly helps", "Ra.One", "Medium", "Nobody in this family has ever heard of an off switch."),
            listOf("A man who stops bullets with his chest falls for a voice on the phone without noticing it belongs to the woman he guards", "Bodyguard", "Hard", "He can stop bullets. He cannot work out who keeps calling him."),
            listOf("A 40-year-old bachelor takes his grandfather's ashes on a Goa trip and is kidnapped by a South Indian don's family instead", "Chennai Express", "Easy", "Never pull strangers onto a moving train, especially ones with a don for a dad."),
            listOf("A very short man from Meerut romances a film star and a scientist, then leaves for Mars because the plot ran out of Earth", "Zero", "Hard", "Meerut to Mars, and the script still couldn't find a landing."),
            listOf("Thieves with no dancing ability join a world dance contest in Dubai to steal diamonds, and somehow become India's pride", "Happy New Year", "Hard", "They planned a diamond heist and accidentally became good at dancing."),
            listOf("A driver's son raps about his problems so well that his problems start paying him", "Gully Boy", "Medium", "His time did come. It took two and a half hours and one very strict dad."),
            listOf("An old landlord and his two broke tenants overhear a ransom call and decide it's a business opportunity", "Hera Pheri", "Easy", "The wrong number that gave us Babu Bhaiya. Best misdial in history."),
            listOf("A gangster bribes his way into medical college, ignores every textbook and treats patients with hugs and carrom", "Munna Bhai M.B.B.S.", "Easy", "A hug cures everything except his attendance record."),
            listOf("A gangster fakes being a history professor to impress a radio jockey, starts seeing a dead freedom fighter and takes his advice", "Lage Raho Munna Bhai", "Medium", "Gandhi's advice works, as long as you skip the history homework."),
            listOf("Pirates in 1795 fight the British with one ship, one bird and a great deal of eyeliner", "Thugs of Hindostan", "Hard", "It cost a fortune. The real thugs were the people selling the tickets."),
            listOf("A man so unlucky that weddings collapse around him tells so many lies abroad that he has to borrow a family", "Housefull", "Hard", "Nothing has gone right for him since scene one, including this summary."),
            listOf("Two strangers in Corsica agree not to share names. Years later he's an accountant having a meltdown about the Ramayana", "Tamasha", "Hard", "He went to Corsica for a holiday and came back with a full identity crisis."),
            listOf("Fourteen songs in, tragedy strikes, and a household pet handles the paperwork that rescues the second wedding", "Hum Aapke Hain Koun", "Easy", "Fourteen songs, no villain and one very hardworking pet."),
            listOf("A surgeon goes through a breakup, so he stops washing, starts operating drunk and calls it love", "Kabir Singh", "Medium", "He is a surgeon. Please don't ask to read his hospital's reviews."),
        )
    }
}
