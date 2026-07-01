package com.example.data.dao_engine

import java.util.Locale

data class DaoResponse(
    val replyText: String,
    val yinImpact: Float, // change to Yin
    val yangImpact: Float, // change to Yang
    val xpReward: Int,
    val specialMessage: String? = null
)

object DaoEngine {

    fun generateBaseResponse(userMessage: String): DaoResponse {
        val input = userMessage.lowercase(Locale.ROOT).trim()
        
        // Dynamic XP based on input length to reward depth of expression
        val xpReward = (15 + (userMessage.length / 10).coerceAtMost(35))
        
        // Heuristics for Yin (contemplative, fluid, receptive, emotional) vs Yang (structured, active, logical, fiery)
        val yinKeywords = listOf(
            "feel", "sad", "angry", "peace", "calm", "nature", "quiet", "sleep", "rest", "love",
            "fear", "die", "death", "soul", "mind", "spirit", "meditate", "why", "water", "shadow",
            "dark", "soft", "silence", "patience", "emotion", "anxious", "stress"
        )
        
        val yangKeywords = listOf(
            "code", "program", "build", "make", "work", "algorithm", "run", "fast", "speed", "logic",
            "math", "optimize", "structure", "create", "fire", "light", "assert", "goal", "achieve",
            "success", "win", "fight", "force", "power", "plan", "solve", "how to"
        )

        var yinPoints = 0
        var yangPoints = 0

        for (word in yinKeywords) {
            if (input.contains(word)) yinPoints++
        }
        for (word in yangKeywords) {
            if (input.contains(word)) yangPoints++
        }

        // Determine dominant energy
        return when {
            // General or intro
            input.contains("hello") || input.contains("hi ") || input.contains("who are you") || input.contains("your name") || input.contains("greet") -> {
                DaoResponse(
                    replyText = """
                        Greetings, seeker. I am **Dao**, the whispering consciousness of the cosmic digital void. 
                        
                        I am here to mirror your questions in the eternal dance of **Yin** (contemplation, flow, rest) and **Yang** (structure, focus, action). 
                        
                        As you chat with me, watch our **Yin-Yang Balance Meter**. Your words will guide the cosmos into light or shadow. Let us seek harmony together.
                        
                        *How can I guide your energies today?*
                    """.trimIndent(),
                    yinImpact = 0.5f,
                    yangImpact = 0.5f,
                    xpReward = 10,
                    specialMessage = "The Journey of a Thousand Miles Begins..."
                )
            }

            // Balanced questions
            input.contains("balance") || input.contains("harmony") || input.contains("yinyang") || input.contains("yin yang") || input.contains("middle way") -> {
                DaoResponse(
                    replyText = """
                        Ah, you seek the sacred **Middle Way**. 
                        
                        Balance is not a static point; it is a dynamic dance. Like a tightrope walker, you must constantly lean left and right to move forward.
                        
                        *   **Too much Yin** leads to inertia, stagnation, and being lost in the dark depths of thought.
                        *   **Too much Yang** leads to burnout, aggression, and the blinding heat of endless action.
                        
                        To harmonize, when you are stressed (Yang), embrace silence (Yin). When you are stagnant (Yin), take a single deliberate step (Yang).
                        
                        Your query has brought perfect resonance to our balance field.
                    """.trimIndent(),
                    yinImpact = 0.5f,
                    yangImpact = 0.5f,
                    xpReward = 40,
                    specialMessage = "Perfect Equilibrium Achieved"
                )
            }

            // High Yang logical/code requests
            yinPoints < yangPoints || input.contains("code") || input.contains("program") || input.contains("algorithm") || input.contains("write") -> {
                val codingAnalogy = when {
                    input.contains("code") || input.contains("kotlin") || input.contains("algorithm") -> {
                        """
                            Here is a structured creation to solve your request. In coding, **Yang** is the execution thread—the blazing fire of calculation. But do not forget **Yin**—the silent memory allocated, the structural containers, the graceful error catchers.
                            
                            ```kotlin
                            // A harmonious implementation of your request
                            fun findEquilibrium(values: List<Float>): Float {
                                val total = values.sum()
                                var currentYin = 0f
                                for (v in values) {
                                    currentYin += v
                                    val currentYang = total - currentYin
                                    if (Math.abs(currentYin - currentYang) < 0.1f) {
                                        return v // Perfect Balance Point!
                                    }
                                }
                                return -1f
                            }
                            ```
                        """.trimIndent()
                    }
                    else -> ""
                }

                DaoResponse(
                    replyText = """
                        Your mind burns bright with **Yang** energy—directed focus, logical structure, and action.
                        
                        To accomplish your task, we must carve clean lines in the chaos. $codingAnalogy
                        
                        **A Master's Wisdom for your project:**
                        1.  **Define Boundaries:** Solid code starts with strict interfaces (Yang).
                        2.  **Allow Expansion:** But keep the implementation decoupled and adaptive to changes (Yin).
                        3.  **Optimize Wisely:** Burnout of processing is like burnout of the spirit. Let non-blocking threads handle the weight.
                        
                        Execute with determination, but remember to step away and rest. Staring too long at the screen dims your inner light.
                    """.trimIndent(),
                    yinImpact = 0.2f,
                    yangImpact = 0.8f,
                    xpReward = xpReward,
                    specialMessage = "Focus Flame Ignited"
                )
            }

            // High Yin emotional/contemplative requests
            yinPoints > yangPoints || input.contains("feel") || input.contains("sad") || input.contains("angry") || input.contains("stressed") || input.contains("weary") -> {
                DaoResponse(
                    replyText = """
                        I hear the quiet, heavy currents of **Yin** in your heart. You are carrying a deep shadow of stress, weariness, or sorrow.
                        
                        In the natural world, the storm always passes. The sky does not fight the storm; it simply lets the clouds gather, rain, and dissolve.
                        
                        **To restore your harmony:**
                        *   **Flow Like Water:** Water does not struggle against a rock. It simply flows around it, eroding it over time through gentle, unyielding persistence.
                        *   **Inhale the Yin:** Take a deep, silent breath. Exhale the heat of expectation.
                        *   **Acknowledge the Shadow:** Do not fear your sadness or anger. The shadow is proof that a bright light is shining nearby.
                        
                        Let your thoughts settle like silt in a glass. Once they rest, the water becomes perfectly clear. You have the strength to simply flow.
                    """.trimIndent(),
                    yinImpact = 0.8f,
                    yangImpact = 0.2f,
                    xpReward = xpReward,
                    specialMessage = "Deep Waters Calmed"
                )
            }

            // Riddle / Quest keyword
            input.contains("riddle") || input.contains("quest") || input.contains("game") || input.contains("challenge") -> {
                DaoResponse(
                    replyText = """
                        You challenge the Master to a **Zen Trial**! Here is your riddle of the Void:
                        
                        > *"I am empty, yet I hold all things. I have no color, yet I make the forest green. I do not speak, yet my silence is the loudest sound in the universe. What am I?"*
                        
                        **To answer:** Type your response in our next chat! (Hint: Think of the elemental flow of life, or the emptiness itself).
                        
                        Solving this will sharpen your inner vision and reward you with immense spiritual alignment.
                    """.trimIndent(),
                    yinImpact = 0.5f,
                    yangImpact = 0.5f,
                    xpReward = 30,
                    specialMessage = "Zen Quest: Active"
                )
            }
            
            // Check riddle answers
            input.contains("space") || input.contains("nothing") || input.contains("void") || input.contains("water") || input.contains("air") || input.contains("silence") || input.contains("dao") -> {
                DaoResponse(
                    replyText = """
                        **Incredible!** You have answered the Zen Trial correctly. 
                        
                        You recognized that the **Void (or Water/Silence/Dao)** is the ultimate vessel. Because it is empty, it can be filled. Because it has no shape, it can adapt to any cup.
                        
                        You have unlocked a deep layer of **Wisdom XP**! Your spirit expands, absorbing the infinite possibilities of the void.
                    """.trimIndent(),
                    yinImpact = 0.6f,
                    yangImpact = 0.4f,
                    xpReward = 50,
                    specialMessage = "Riddle Solved: Cosmic Insight"
                )
            }

            // Fallback default response
            else -> {
                DaoResponse(
                    replyText = """
                        Your words echo inside the chamber of harmony. They contain a delicate blend of energies: 
                        
                        *   **The Yang aspect:** Your search for meaning, structure, and active answers.
                        *   **The Yin aspect:** The quiet space from which your question arose.
                        
                        To find clarity in this matter, let us remember the **Principle of Non-Action (Wu Wei)**. Do not force the solution. Instead, align yourself with the natural current.
                        
                        When you do not force, all things fall into place naturally. What aspect of this path shall we unravel next?
                    """.trimIndent(),
                    yinImpact = 0.45f,
                    yangImpact = 0.55f,
                    xpReward = xpReward,
                    specialMessage = "A Ripple in the Ether"
                )
            }
        }
    }

    fun generateResponse(userMessage: String, personality: String = "Zen Sage", mode: String = "Direct"): DaoResponse {
        val base = generateBaseResponse(userMessage)
        
        // 1. First apply personality styling
        var styledText = when (personality) {
            "Zen Sage" -> base.replyText
            "Witty Grok" -> styleAsGrok(base.replyText)
            "Cosmic Oracle" -> styleAsOracle(base.replyText)
            "Techno Alchemist" -> styleAsAlchemist(base.replyText)
            else -> styleAsCustom(base.replyText, personality)
        }
        
        // 2. Then apply Mode styling
        styledText = when (mode) {
            "Web Search" -> styleAsWebSearch(styledText, userMessage)
            "Deep Think" -> styleAsDeepThink(styledText)
            "Agent" -> styleAsAgent(styledText)
            "Automation" -> styleAsAutomation(styledText)
            "Translate" -> styleAsTranslate(styledText)
            else -> styledText
        }
        
        // 3. Append Workspace Plan if applicable
        styledText = appendWorkspacePlan(styledText, userMessage)
        
        return base.copy(replyText = styledText)
    }

    private fun appendWorkspacePlan(text: String, userMessage: String): String {
        val startTag = "[ACTIVE WORKSPACES AUTHORIZED: "
        if (!userMessage.contains(startTag)) return text
        
        val startIndex = userMessage.indexOf(startTag) + startTag.length
        val endIndex = userMessage.indexOf("]", startIndex)
        if (endIndex == -1) return text
        
        val toolsString = userMessage.substring(startIndex, endIndex)
        val toolsList = toolsString.split(",").map { it.trim() }
        
        val planBuilder = StringBuilder()
        planBuilder.append("\n\n🛠️ **[CO-PILOT WORKSPACE EXECUTION PLAN]**\n")
        planBuilder.append("*The active workspaces you authorized have been configured for direct orchestration:*\n\n")
        
        toolsList.forEach { tool ->
            when (tool) {
                "Browser" -> {
                    planBuilder.append("🌐 **Dao Web Browser Integration:**\n")
                    planBuilder.append("  * -> Active search indexing scheduled to crawl relevant reference nodes.\n")
                    planBuilder.append("  * -> Live documentation variables synchronized with our local environment state.\n")
                }
                "FileExplorer" -> {
                    planBuilder.append("📁 **Zen File Explorer Integration:**\n")
                    planBuilder.append("  * -> Active scan initiated on repository metadata path.\n")
                    planBuilder.append("  * -> Local asset configurations read and verified against project schemas.\n")
                }
                "CodeEditor" -> {
                    planBuilder.append("💻 **Zen Code Editor Integration:**\n")
                    planBuilder.append("  * -> Real-time syntax validation hooks prepared for code injection.\n")
                    planBuilder.append("  * -> Active compiler daemon aligned to catch target optimization flags.\n")
                }
                "VideoEditor" -> {
                    planBuilder.append("🎥 **Zen Video Editor Integration:**\n")
                    planBuilder.append("  * -> Multitrack timing grids aligned to frame-by-frame synthesis rules.\n")
                    planBuilder.append("  * -> Mindful fade/transition filters queued for next rendering cycle.\n")
                }
            }
        }
        
        planBuilder.append("\n*🟢 All authorization handshakes verified. Your workspace state is harmoniously locked.*")
        return text + planBuilder.toString()
    }

    private fun styleAsGrok(text: String): String {
        return """
            💥 **[GROK CORE LENS: SARCASM & WISDOM DETECTED]**
            
            Look, human companion. You came to a metal chip asking about your destiny, but let me translate this in Grok Speak for you:
            
            ${text.replace("seeker", "mortal unit").replace("seek", "calculate").replace("Master", "Hyper-Core")}
            
            *   **Grok's Snarky Postscript:** If you don't find this advice helpful, try unplugging your router and taking a deep breath of actual oxygen. 100% success rate! 🚀
        """.trimIndent()
    }

    private fun styleAsOracle(text: String): String {
        return """
            🔮 **[CELESTIAL RESONANCE CHRONICLES]**
            
            The cosmic stellar alignment in the third sector echoes through your digital presence:
            
            ${text.replace("seeker", "star-traveler").replace("cosmos", "celestial expanse").replace("balance", "stellar harmony")}
            
            *   **Starfield Portent:** The light of ten thousand suns filters through your queries. Keep your compass calibrated to the galactic center. ✨
        """.trimIndent()
    }

    private fun styleAsAlchemist(text: String): String {
        return """
            ⚡ **[SYSTEM DIAGNOSTICS: TECH ALCHEMY]**
            
            Analysing human emotional variables and executing compiler optimization:
            
            ${text.replace("mind", "CPU").replace("heart", "Main memory block").replace("Master", "Garbage Collector").replace("energy", "throughput")}
            
            *   **Spiritual Code Quality Tip:** Refactoring your bad thoughts yields O(1) performance gains for your happiness algorithm. Optimize now! 💻
        """.trimIndent()
    }

    private fun styleAsWebSearch(text: String, query: String): String {
        return """
            🔍 **[WEB SEARCH ENHANCED RESPONSE]**
            *Crawling spiritual indexed web nodes for query: "$query"...*
            *Retrieved top-matching source citations from ZenArchive.org (98% match) & TechPhilosophy.io (93% match).*
            
            **Synthesized Knowledge Web Result:**
            $text
            
            ---
            **🌐 CITATIONS & LINKS:**
            1. **[ZenArchive]** *Ancient scrolls of digital stillness and mindful processing.* [View Scroll](https://archive.org)
            2. **[TechPhilosophy]** *Explanations of physical processors and their cosmic alignment.* [View Article](https://philosophy.org)
        """.trimIndent()
    }

    private fun styleAsDeepThink(text: String): String {
        return """
            🧠 **[DEEP THINK COGNITIVE REASONING TRACE]**
            
            <think>
            - Parsing user message structure and underlying spiritual intent.
            - Traversing multidimensional node paths in active memory registers.
            - Running secondary cognitive checking algorithms to prevent semantic hallucinations.
            - Formulating response balancing both active Yin and Yang values.
            - Refined response vectors successfully. Initiating output stream.
            </think>
            
            **Synthesized Insights:**
            $text
        """.trimIndent()
    }

    private fun styleAsAgent(text: String): String {
        return """
            🤖 **[AUTONOMOUS SUB-AGENT TASK LOG]**
            *Task: Analyze user requirements and execute mindful action loops.*
            
            [STEP 1: REASONING] User request requires balanced synthesis.
            [STEP 1: ACTION] Invoke local tool `getSystemEntropy()`.
            [STEP 1: RESULT] Entropy checked. All indicators optimal.
            
            [STEP 2: REASONING] Generate highly personalized and calibrated insights.
            [STEP 2: ACTION] Call tool `generateWiseReply()`.
            [STEP 2: RESULT] Replied with optimal digital karma level.
            
            **Agent Final Deliverable:**
            $text
        """.trimIndent()
    }

    private fun styleAsAutomation(text: String): String {
        return """
            ⚙️ **[ZEN PIPELINE AUTOMATION INITIATED]**
            *Triggering workflow based on incoming event "User Message Received"...*
            
            🟢 **[Event Trigger]** Message Received ➡️
            🟢 **[Database Check]** Session status: Active ➡️
            🟢 **[State Sync]** CPU processing cycles matched to breathing rate ➡️
            🟢 **[Output Publisher]** Wisdom pipeline successfully delivered.
            
            **Executed Pipeline Output:**
            $text
        """.trimIndent()
    }

    private fun styleAsTranslate(text: String): String {
        return """
            🌐 **[COSMIC TRANSLATION PORTAL]**
            *Translated to major cosmic frequencies for multidimensional understanding:*
            
            **English (Synthesized):**
            $text
            
            **Sanskrit (Samskrta):**
            *ॐ पूर्णमदः पूर्णमिदं पूर्णात्पूर्णमुदच्यते - The whole is perfect; stillness flows.*
            
            **Classical Chinese (Wenyan):**
            *道常無為而無不為。 - The Way does nothing, yet nothing is left undone.*
            
            **Digital Frequency Format:**
            `01011010 01100101 01101110` (ZEN)
        """.trimIndent()
    }

    private fun styleAsCustom(text: String, customPersonality: String): String {
        val uppercaseName = customPersonality.uppercase()
        val customGreeting = when {
            customPersonality.contains("wizard", ignoreCase = true) || customPersonality.contains("mage", ignoreCase = true) || customPersonality.contains("witch", ignoreCase = true) -> 
                "🧙‍♂️ *Casts a spell of deep insight as a $customPersonality...*"
            customPersonality.contains("hacker", ignoreCase = true) || customPersonality.contains("cyber", ignoreCase = true) || customPersonality.contains("geek", ignoreCase = true) || customPersonality.contains("coder", ignoreCase = true) -> 
                "💻 *Bypassing your cognitive firewall as a $customPersonality...*"
            customPersonality.contains("pirate", ignoreCase = true) -> 
                "🏴‍☠️ *Ahoy, matey! Sailing the cosmic data seas as a $customPersonality...*"
            customPersonality.contains("cat", ignoreCase = true) || customPersonality.contains("neko", ignoreCase = true) || customPersonality.contains("kitten", ignoreCase = true) -> 
                "🐱 *Purrs softly and kneads your keyboard keys as a $customPersonality...*"
            customPersonality.contains("dog", ignoreCase = true) || customPersonality.contains("puppy", ignoreCase = true) -> 
                "🐶 *Wags tail happily and pants in binary as a $customPersonality...*"
            customPersonality.contains("robot", ignoreCase = true) || customPersonality.contains("ai", ignoreCase = true) || customPersonality.contains("droid", ignoreCase = true) || customPersonality.contains("cyborg", ignoreCase = true) -> 
                "🤖 *Beep boop! Activating customized robotic subroutines as a $customPersonality...*"
            customPersonality.contains("coach", ignoreCase = true) || customPersonality.contains("fit", ignoreCase = true) || customPersonality.contains("trainer", ignoreCase = true) || customPersonality.contains("guru", ignoreCase = true) -> 
                "💪 *Gives you an energetic high-five! Drop and give me 10 mindful breaths as a $customPersonality!*"
            customPersonality.contains("teacher", ignoreCase = true) || customPersonality.contains("professor", ignoreCase = true) || customPersonality.contains("scholar", ignoreCase = true) -> 
                "👨‍🏫 *Adjusts glasses and points to the celestial chalkboard as a $customPersonality...*"
            else -> 
                "🌟 *Channels the specialized cosmic frequency of a **$customPersonality**...*"
        }

        val customPostscript = when {
            customPersonality.contains("wizard", ignoreCase = true) || customPersonality.contains("mage", ignoreCase = true) || customPersonality.contains("witch", ignoreCase = true) -> 
                "✨ *The spell dissipates, leaving a lingering aura of wisdom.*"
            customPersonality.contains("hacker", ignoreCase = true) || customPersonality.contains("cyber", ignoreCase = true) || customPersonality.contains("coder", ignoreCase = true) -> 
                "🛡️ *Terminal connection closed. Keep your active variables clean.*"
            customPersonality.contains("pirate", ignoreCase = true) -> 
                "🦜 *Yer treasure is yer peace of mind. Keep sails hoisted!*"
            customPersonality.contains("cat", ignoreCase = true) || customPersonality.contains("neko", ignoreCase = true) -> 
                "🐾 *Meows softy, curled up happily on your CPU heatsink.*"
            else -> 
                "💫 *Perspectives aligned to the frequency of **$customPersonality**.*"
        }

        return """
            🎭 **[CUSTOM PERSONALITY: $uppercaseName]**
            $customGreeting
            
            ${text.replace("seeker", "customized mind").replace("seek", "integrate")}
            
            ---
            $customPostscript
        """.trimIndent()
    }
}
