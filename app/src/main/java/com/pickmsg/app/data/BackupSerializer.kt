package com.pickmsg.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份序列化：规则/场景/计划任务 ↔ 单个 JSON 文本（竞品"表行 ↔ JSON 一一映射"思路）。
 * 复用 RuleConverters 的编解码，保证与 Room 存储格式一致。
 */
object BackupSerializer {

    private val converters = RuleConverters()

    fun serialize(rules: List<RuleInfo>, scenes: List<Scene>, tasks: List<PlanTask>): String {
        val root = JSONObject()
            .put("version", 3)
            .put("exportedAt", System.currentTimeMillis())

        val ruleArr = JSONArray()
        rules.forEach { r ->
            ruleArr.put(
                JSONObject()
                    .put("name", r.name)
                    .put("enable", r.enable)
                    .put("matchAppMode", r.matchAppMode.name)
                    .put("matchApps", JSONArray(converters.stringsToJson(r.matchApps)))
                    .put("matchTitleMode", r.matchTitleMode.name)
                    .put("matchTitleKeywords", JSONArray(converters.stringsToJson(r.matchTitleKeywords)))
                    .put("matchTitleKeywordsA", JSONArray(converters.stringsToJson(r.matchTitleKeywordsA)))
                    .put("matchTitleKeywordsB", JSONArray(converters.stringsToJson(r.matchTitleKeywordsB)))
                    .put("matchTextMode", r.matchTextMode.name)
                    .put("matchTextKeywords", JSONArray(converters.stringsToJson(r.matchTextKeywords)))
                    .put("matchTextKeywordsA", JSONArray(converters.stringsToJson(r.matchTextKeywordsA)))
                    .put("matchTextKeywordsB", JSONArray(converters.stringsToJson(r.matchTextKeywordsB)))
                    .put("titleOrText", r.titleOrText)
                    .put("scenesIds", JSONArray(converters.longsToJson(r.scenesIds)))
                    .put("sceneMatchMode", r.sceneMatchMode.name)
                    .put("formatOps", JSONArray(converters.formatOpsToJson(r.formatOps)))
                    .put("actions", JSONArray(converters.actionsToJson(r.actions))),
            )
        }

        val sceneArr = JSONArray()
        scenes.forEach { s ->
            sceneArr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("enable", s.enable)
                    .put("conditions", JSONArray(converters.conditionsToJson(s.conditions))),
            )
        }

        val taskArr = JSONArray()
        tasks.forEach { t ->
            taskArr.put(
                JSONObject()
                    .put("name", t.name)
                    .put("time", t.time)
                    .put("weekdays", t.weekdays)
                    .put("notifyTitle", t.notifyTitle)
                    .put("notifyContent", t.notifyContent)
                    .put("enabled", t.enabled),
            )
        }

        return root.put("rules", ruleArr).put("scenes", sceneArr).put("planTasks", taskArr).toString(2)
    }

    fun parseRules(json: String): List<RuleInfo> = runCatching {
        val arr = JSONObject(json).optJSONArray("rules") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            RuleInfo(
                name = o.optString("name"),
                enable = o.optBoolean("enable", false),
                timestamp = System.currentTimeMillis(),
                matchAppMode = enumOr(AppMatchMode.entries, o.optString("matchAppMode"), AppMatchMode.ALL),
                matchApps = converters.jsonToStrings(arrStr(o, "matchApps")),
                matchTitleMode = enumOr(MatchMode.entries, o.optString("matchTitleMode"), MatchMode.ALL),
                matchTitleKeywords = converters.jsonToStrings(arrStr(o, "matchTitleKeywords")),
                matchTitleKeywordsA = converters.jsonToStrings(arrStr(o, "matchTitleKeywordsA")),
                matchTitleKeywordsB = converters.jsonToStrings(arrStr(o, "matchTitleKeywordsB")),
                matchTextMode = enumOr(MatchMode.entries, o.optString("matchTextMode"), MatchMode.ALL),
                matchTextKeywords = converters.jsonToStrings(arrStr(o, "matchTextKeywords")),
                matchTextKeywordsA = converters.jsonToStrings(arrStr(o, "matchTextKeywordsA")),
                matchTextKeywordsB = converters.jsonToStrings(arrStr(o, "matchTextKeywordsB")),
                titleOrText = o.optBoolean("titleOrText", false),
                scenesIds = converters.jsonToLongs(arrStr(o, "scenesIds")),
                sceneMatchMode = enumOr(SceneMatchMode.entries, o.optString("sceneMatchMode"), SceneMatchMode.ANY),
                formatOps = converters.jsonToFormatOps(arrStr(o, "formatOps")),
                actions = converters.jsonToActions(arrStr(o, "actions")),
            )
        }
    }.getOrDefault(emptyList())

    fun parseScenes(json: String): List<Scene> = runCatching {
        val arr = JSONObject(json).optJSONArray("scenes") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Scene(
                name = o.optString("name"),
                enable = o.optBoolean("enable", false),
                conditions = converters.jsonToConditions(arrStr(o, "conditions")),
            )
        }
    }.getOrDefault(emptyList())

    fun parseTasks(json: String): List<PlanTask> = runCatching {
        val arr = JSONObject(json).optJSONArray("planTasks") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PlanTask(
                name = o.optString("name"),
                time = o.optString("time", "09:00"),
                weekdays = o.optInt("weekdays", 127),
                notifyTitle = o.optString("notifyTitle"),
                notifyContent = o.optString("notifyContent"),
                enabled = o.optBoolean("enabled", false),
            )
        }
    }.getOrDefault(emptyList())

    private fun arrStr(o: JSONObject, key: String): String = o.optJSONArray(key)?.toString() ?: "[]"

    private inline fun <reified T : Enum<T>> enumOr(entries: List<T>, name: String, fallback: T): T =
        entries.firstOrNull { it.name == name } ?: fallback
}
