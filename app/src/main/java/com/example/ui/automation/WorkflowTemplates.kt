package com.example.ui.automation

import org.json.JSONArray
import org.json.JSONObject

data class WorkflowTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val author: String,
    val steps: List<String>,
    val downloads: Int
)

object WorkflowMarketplace {
    val builtInTemplates = listOf(
        WorkflowTemplate("1", "Daily News Summary", "Searches news, summarizes, and saves to notes", "Productivity", "Dao Team",
            listOf("web_search:{\"query\":\"top news today\"}", "notes_create:{\"title\":\"Daily News\"}"), 1200),
        WorkflowTemplate("2", "Code Review Bot", "Reviews GitHub PRs automatically", "Development", "Dao Team",
            listOf("github_fetch_pr", "ai_review", "github_post_review"), 850),
        WorkflowTemplate("3", "Morning Routine", "Weather, calendar, news briefing", "Lifestyle", "Dao Team",
            listOf("web_search:weather", "notification_summary", "calendar_today"), 2100),
        WorkflowTemplate("4", "File Organizer", "Sorts Downloads folder by type", "File Management", "Dao Team",
            listOf("file_list:/sdcard/Download", "file_sort_by_type", "file_move_to_folders"), 670),
        WorkflowTemplate("5", "Research Assistant", "Deep research on any topic", "Research", "Dao Team",
            listOf("web_search", "web_scrape_top_5", "summarize", "create_report"), 1500),
        WorkflowTemplate("6", "Social Media Scheduler", "Schedules posts via Telegram", "Social", "Dao Team",
            listOf("content_create", "telegram_schedule", "telegram_post"), 430)
    )

    fun getTemplatesByCategory(category: String): List<WorkflowTemplate> {
        return if (category == "All") builtInTemplates
        else builtInTemplates.filter { it.category == category }
    }

    fun importTemplate(template: WorkflowTemplate): List<Pair<String, Map<String, String>>> {
        return template.steps.map { step ->
            val parts = step.split(":", limit = 2)
            val action = parts[0]
            val params = if (parts.size > 1) {
                try {
                    val json = JSONObject(parts[1])
                    json.keys().asSequence().associate { it to json.getString(it) }
                } catch (e: Exception) { emptyMap() }
            } else emptyMap()
            action to params
        }
    }

    fun exportTemplate(name: String, description: String, steps: List<String>): String {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("steps", JSONArray(steps))
        }.toString(2)
    }
}
