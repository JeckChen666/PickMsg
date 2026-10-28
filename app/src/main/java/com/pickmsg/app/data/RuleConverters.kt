package com.pickmsg.app.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/**
 * 列表/枚举字段存 JSON 文本。
 * 解析遇到未知枚举值或坏行时跳过该项，不让单条坏数据拖垮整表读取。
 */
class RuleConverters {

    @TypeConverter
    fun stringsToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToStrings(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotEmpty() } }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun longsToJson(value: List<Long>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToLongs(json: String): List<Long> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.optLong(it) }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun formatOpsToJson(value: List<FormatOp>): String {
        val arr = JSONArray()
        value.forEach { op ->
            arr.put(
                JSONObject()
                    .put("op", op.op.name)
                    .put("target", op.target.name)
                    .put("from", op.from)
                    .put("to", op.to),
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun jsonToFormatOps(json: String): List<FormatOp> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val op = o.optString("op").let { name -> FormatOpType.entries.firstOrNull { it.name == name } }
                ?: return@mapNotNull null
            val target = o.optString("target").let { name -> FormatTarget.entries.firstOrNull { it.name == name } }
                ?: FormatTarget.TEXT
            FormatOp(op = op, target = target, from = o.optString("from"), to = o.optString("to"))
        }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun actionsToJson(value: List<ActionSpec>): String {
        val arr = JSONArray()
        value.forEach { a ->
            val o = JSONObject()
                .put("type", a.type.name)
                .put("delaySec", a.delaySec)
                .put("titleTemplate", a.titleTemplate)
                .put("textTemplate", a.textTemplate)
                .put("soundUri", a.soundUri)
                .put("force", a.force)
                .put("vibratePattern", a.vibratePattern)
                .put("ttsTemplate", a.ttsTemplate)
                .put("url", a.url)
                .put("method", a.method)
                .put("headers", a.headers)
                .put("body", a.body)
                .put("emailTo", a.emailTo)
                .put("phoneNumbers", JSONArray(a.phoneNumbers))
                .put("repeatTimes", a.repeatTimes)
                .put("intervalSec", a.intervalSec)
            arr.put(o)
        }
        return arr.toString()
    }

    @TypeConverter
    fun jsonToActions(json: String): List<ActionSpec> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type").let { name -> ActionType.entries.firstOrNull { it.name == name } }
                ?: return@mapNotNull null
            val numbers = mutableListOf<String>()
            runCatching {
                val n = o.optJSONArray("phoneNumbers")
                if (n != null) for (j in 0 until n.length()) numbers.add(n.optString(j))
            }
            ActionSpec(
                type = type,
                delaySec = o.optInt("delaySec", 0),
                titleTemplate = o.optString("titleTemplate"),
                textTemplate = o.optString("textTemplate"),
                soundUri = o.optString("soundUri"),
                force = o.optBoolean("force", false),
                vibratePattern = o.optString("vibratePattern"),
                ttsTemplate = o.optString("ttsTemplate"),
                url = o.optString("url"),
                method = o.optString("method", "POST"),
                headers = o.optString("headers"),
                body = o.optString("body"),
                emailTo = o.optString("emailTo"),
                phoneNumbers = numbers,
                repeatTimes = o.optInt("repeatTimes", 0),
                intervalSec = o.optInt("intervalSec", 0),
            )
        }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun conditionsToJson(value: List<SceneCondition>): String {
        val arr = JSONArray()
        value.forEach { c ->
            val kinds = JSONArray()
            c.networkKinds.forEach { kinds.put(it.name) }
            arr.put(
                JSONObject()
                    .put("type", c.type.name)
                    .put("networkKinds", kinds)
                    .put("ssids", JSONArray(c.ssids))
                    .put("startTime", c.startTime)
                    .put("endTime", c.endTime)
                    .put("minBattery", c.minBattery)
                    .put("maxBattery", c.maxBattery)
                    .put("weekdayMask", c.weekdayMask)
                    .put("screenOn", c.screenOn)
                    .put("landscape", c.landscape),
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun jsonToConditions(json: String): List<SceneCondition> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type").let { name -> SceneConditionType.entries.firstOrNull { it.name == name } }
                ?: return@mapNotNull null
            val kinds = mutableListOf<NetworkKind>()
            runCatching {
                val k = o.optJSONArray("networkKinds")
                if (k != null) for (j in 0 until k.length()) {
                    NetworkKind.entries.firstOrNull { it.name == k.optString(j) }?.let(kinds::add)
                }
            }
            val ssids = mutableListOf<String>()
            runCatching {
                val s = o.optJSONArray("ssids")
                if (s != null) for (j in 0 until s.length()) ssids.add(s.optString(j))
            }
            SceneCondition(
                type = type,
                networkKinds = kinds,
                ssids = ssids,
                startTime = o.optString("startTime", "00:00"),
                endTime = o.optString("endTime", "23:59"),
                minBattery = o.optInt("minBattery", 0),
                maxBattery = o.optInt("maxBattery", 100),
                weekdayMask = o.optInt("weekdayMask", 127),
                screenOn = o.optBoolean("screenOn", true),
                landscape = o.optBoolean("landscape", false),
            )
        }
    }.getOrDefault(emptyList())
}
