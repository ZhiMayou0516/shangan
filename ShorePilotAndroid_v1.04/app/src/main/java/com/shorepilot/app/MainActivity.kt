package com.shorepilot.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var currentScreen = "dashboard"

    private val bg = Color.rgb(247, 248, 250)
    private val cardBg = Color.WHITE
    private val textMain = Color.rgb(20, 25, 35)
    private val textSub = Color.rgb(92, 101, 116)
    private val blue = Color.rgb(37, 99, 235)
    private val green = Color.rgb(22, 163, 74)
    private val red = Color.rgb(220, 38, 38)
    private val amber = Color.rgb(217, 119, 6)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("shorepilot_data", MODE_PRIVATE)
        setContentView(shell())
        renderDashboard()
    }

    private fun shell(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(18), dp(18), dp(18), dp(8))
        header.addView(label("上岸作战舱", 25f, true, textMain))
        header.addView(label("目标、日历、体重、预算和日常事务，放在同一个仪表盘里。", 13f, false, textSub))
        root.addView(header)

        val scrollNav = HorizontalScrollView(this)
        scrollNav.isHorizontalScrollBarEnabled = false
        nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setPadding(dp(12), dp(4), dp(12), dp(10))
        scrollNav.addView(nav)
        root.addView(scrollNav)

        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(14), dp(2), dp(14), dp(24))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        rebuildNav()
        return root
    }

    private fun rebuildNav() {
        nav.removeAllViews()
        addNav("首页", "dashboard")
        addNav("目标", "goals")
        addNav("日历", "calendar")
        addNav("复习", "study")
        addNav("运动", "fitness")
        addNav("开支", "budget")
        addNav("事务", "tasks")
        addNav("复盘", "review")
    }

    private fun addNav(title: String, screen: String) {
        val b = Button(this)
        b.text = title
        b.textSize = 14f
        b.setTextColor(if (screen == currentScreen) Color.WHITE else blue)
        b.setBackgroundColor(if (screen == currentScreen) blue else Color.WHITE)
        b.setPadding(dp(12), 0, dp(12), 0)
        val lp = LinearLayout.LayoutParams(-2, dp(42))
        lp.setMargins(dp(4), 0, dp(4), 0)
        nav.addView(b, lp)
        b.setOnClickListener {
            when (screen) {
                "dashboard" -> renderDashboard()
                "goals" -> renderGoals()
                "calendar" -> renderCalendar()
                "study" -> renderStudy()
                "fitness" -> renderFitness()
                "budget" -> renderBudget()
                "tasks" -> renderTasks()
                "review" -> renderReview()
            }
        }
    }

    private fun setScreen(screen: String) {
        currentScreen = screen
        rebuildNav()
        content.removeAllViews()
    }

    private fun renderDashboard() {
        setScreen("dashboard")
        val nearest = nearestGoal()
        val nearestName = nearest.optString("name", "考研")
        val nearestDate = parseDate(nearest.optString("deadline", "2026-12-20")) ?: LocalDate.parse("2026-12-20")
        val days = ChronoUnit.DAYS.between(LocalDate.now(), nearestDate)
        val studyStats = studyStats()
        val latestWeight = latestWeight()
        val startWeight = firstWeight()
        val targetLoss = targetLossJin()
        val lostJin = if (latestWeight > 0.0 && startWeight > 0.0) (startWeight - latestWeight) * 2.0 else 0.0
        val monthExpense = monthExpense()
        val budget = effectiveMonthlyBudget()
        val remaining = budget - monthExpense
        val dailyCanSpend = remaining / max(1, daysLeftInMonth())
        val dailyTasks = dailyTasksOpenCount()

        content.addView(metricCard("最近DDL · $nearestName", "D-${max(0, days.toInt())}", "目标日期 $nearestDate，可在目标页添加或修改多个 DDL。", blue))

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(smallMetric("复习完成", "${studyStats.percent.roundToInt()}%", "已完成 ${one(studyStats.doneHours)} / ${one(studyStats.totalHours)} h", green), weightLp())
        row1.addView(smallMetric("本周学习", "${one(studyStats.weekDone)} h", "目标 ${one(weeklyStudyGoal())} h", blue), weightLp())
        content.addView(row1)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val weightText = if (latestWeight > 0.0) "${one(lostJin)} 斤" else "待记录"
        row2.addView(smallMetric("减重进度", weightText, "目标 -${one(targetLoss)} 斤，按 7 日趋势看", amber), weightLp())
        row2.addView(smallMetric("今日可花", "${one(dailyCanSpend)} 元", "本月剩余 ${one(remaining)} 元", if (remaining >= 0) green else red), weightLp())
        content.addView(row2)

        val warning = when {
            studyStats.debt > weeklyStudyGoal() * 0.25 -> "复习偏紧：本周学习债务 ${one(studyStats.debt)} h，建议今晚优先补最薄弱科目。"
            remaining < 0 -> "预算超支：本月已经超过预算 ${one(-remaining)} 元，接下来几天先压低可砍开支。"
            latestWeight <= 0.0 -> "还没记录体重：先录入一次当前体重，后面 App 才能算减重趋势。"
            else -> "状态可控：今天把学习任务、开支和运动各记一次，这个系统就开始有用了。"
        }
        content.addView(card("今日提醒", label(warning, 15f, false, textMain)))
        content.addView(card("目标概览", goalsOverviewView()))
        content.addView(card("未来 7 天", agendaView(7)))
        content.addView(card("待办概览", label("还有 $dailyTasks 个日常事务未完成。复习任务完成后，晚上用复盘页看一下本周是否偏离目标。", 15f, false, textMain)))
    }

    private fun renderGoals() {
        setScreen("goals")
        val goals = goalsArr()
        content.addView(metricCard("目标管理", "${goals.length()} 个目标", "把考研、论文、项目、证书、比赛等 DDL 都放进来，首页会自动显示最近的。", blue))

        val lossInput = input("减重目标（斤），例如 10 / 20", one(targetLossJin()))
        lossInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val lossBox = LinearLayout(this)
        lossBox.orientation = LinearLayout.VERTICAL
        lossBox.addView(lossInput)
        lossBox.addView(action("保存减重目标") {
            prefs.edit().putFloat("targetLossJin", num(lossInput, 20.0).toFloat()).apply()
            renderGoals()
        })
        content.addView(card("身体目标", lossBox))

        val name = input("目标名称，如 考研 / 论文 / 比赛 / 实习申请", "")
        val deadline = input("DDL 日期，例如 2026-12-20", today())
        val weeklyHours = input("每周投入目标 h，没有就填 0", "0")
        weeklyHours.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val note = input("备注，可不填", "")
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.addView(name)
        form.addView(deadline)
        form.addView(weeklyHours)
        form.addView(note)
        form.addView(action("添加目标") {
            val n = name.text.toString().trim()
            val d = deadline.text.toString().trim()
            if (n.isEmpty()) {
                toast("目标名称要写一下")
                return@action
            }
            runCatching { LocalDate.parse(d) }.onFailure {
                toast("日期格式要像 ${today()}")
                return@action
            }
            val a = goalsArr()
            a.put(JSONObject()
                .put("id", System.currentTimeMillis())
                .put("name", n)
                .put("deadline", d)
                .put("weeklyHours", num(weeklyHours, 0.0))
                .put("note", note.text.toString().trim()))
            saveArr("goals", a)
            renderGoals()
        })
        content.addView(card("新增目标 / DDL", form))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        for (i in 0 until goals.length()) {
            val g = goals.getJSONObject(i)
            val goalName = g.optString("name")
            val date = parseDate(g.optString("deadline"))
            val days = if (date != null) ChronoUnit.DAYS.between(LocalDate.now(), date).toInt() else 0
            val stats = goalStudyStats(goalName)
            val noteText = if (g.optString("note").isNotEmpty()) " · ${g.optString("note")}" else ""
            list.addView(taskRow(
                title = "$goalName · D-${max(0, days)}",
                sub = "DDL ${g.optString("deadline")} · 周目标 ${one(g.optDouble("weeklyHours", 0.0))} h · 已完成 ${one(stats.doneHours)} / ${one(stats.totalHours)} h$noteText",
                done = false,
                onToggle = {
                    toast("目标不需要勾完成，可以删除或继续挂任务")
                },
                onDelete = {
                    goals.remove(i)
                    saveArr("goals", goals)
                    renderGoals()
                }
            ))
        }
        content.addView(card("目标列表", list))
    }

    private fun renderCalendar() {
        setScreen("calendar")
        val next7 = agendaOpenCount(7)
        val todayCount = agendaCountFor(LocalDate.now(), onlyOpen = true)
        content.addView(metricCard("一周前瞻", "$next7 个待办", "今天还有 $todayCount 个未完成。周末可以提前把下周大任务排进来。", blue))

        val date = input("计划日期，例如 ${today()}", today())
        val category = input("类型：学习 / 生活 / 运动 / 其他", "学习")
        val goalName = input("所属目标，仅学习任务需要", nearestGoal().optString("name", "考研"))
        val title = input("待办内容，如 数学真题 2018 / 洗衣服", "")
        val hours = input("预计学习时长 h，仅学习任务需要", "1.5")
        hours.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.addView(date)
        form.addView(category)
        form.addView(goalName)
        form.addView(title)
        form.addView(hours)
        form.addView(action("添加到日历") {
            val d = date.text.toString().trim()
            runCatching { LocalDate.parse(d) }.onFailure {
                toast("日期格式要像 ${today()}")
                return@action
            }
            val t = title.text.toString().trim()
            if (t.isEmpty()) {
                toast("写一下要做什么")
                return@action
            }
            if (category.text.toString().contains("学")) {
                val a = arr("studyTasks")
                a.put(JSONObject()
                    .put("id", System.currentTimeMillis())
                    .put("goal", goalName.text.toString().trim().ifEmpty { nearestGoal().optString("name", "考研") })
                    .put("subject", "日历")
                    .put("title", t)
                    .put("hours", num(hours, 1.0))
                    .put("done", false)
                    .put("created", today())
                    .put("planDate", d)
                    .put("doneAt", ""))
                saveArr("studyTasks", a)
            } else {
                val a = arr("dailyTasks")
                a.put(JSONObject()
                    .put("id", System.currentTimeMillis())
                    .put("title", "${category.text.toString().trim()} · $t")
                    .put("done", false)
                    .put("created", today())
                    .put("planDate", d))
                saveArr("dailyTasks", a)
            }
            renderCalendar()
        })
        content.addView(card("快速添加", form))
        content.addView(card("未来 7 天安排", agendaView(7)))
    }

    private fun renderStudy() {
        setScreen("study")
        val stats = studyStats()
        content.addView(metricCard("学习总进度", "${stats.percent.roundToInt()}%", "学习债务 ${one(stats.debt)} h；本周完成 ${one(stats.weekDone)} / ${one(weeklyStudyGoal())} h。", if (stats.debt > 8) amber else green))

        val weeklyInput = input("本周临时学习目标 h，例如 20 / 30 / 42", one(weeklyStudyGoal()))
        weeklyInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val settings = LinearLayout(this)
        settings.orientation = LinearLayout.VERTICAL
        settings.addView(label("这里改的是这一周的目标，不会影响目标页里的长期 DDL。", 14f, false, textSub))
        settings.addView(weeklyInput)
        settings.addView(action("保存本周目标") {
            prefs.edit().putFloat("weeklyStudyOverride", num(weeklyInput, 0.0).toFloat()).apply()
            renderStudy()
        })
        settings.addView(action("清除临时目标，按目标页合计") {
            prefs.edit().remove("weeklyStudyOverride").apply()
            renderStudy()
        })
        content.addView(card("本周目标", settings))

        val goalName = input("所属目标，如 考研 / 论文 / 比赛", nearestGoal().optString("name", "考研"))
        val subject = input("科目，如 数学/英语/专业课", "")
        val title = input("任务，如 线代强化题 30 题", "")
        val planDate = input("计划日期，例如 ${today()}", today())
        val hours = input("预计用时 h", "1.5")
        hours.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.addView(goalName)
        form.addView(subject)
        form.addView(title)
        form.addView(planDate)
        form.addView(hours)
        form.addView(action("添加复习任务") {
            val s = subject.text.toString().trim()
            val t = title.text.toString().trim()
            val g = goalName.text.toString().trim()
            if (g.isEmpty() || s.isEmpty() || t.isEmpty()) {
                toast("目标、科目和任务都要写一下")
                return@action
            }
            val d = planDate.text.toString().trim()
            runCatching { LocalDate.parse(d) }.onFailure {
                toast("日期格式要像 ${today()}")
                return@action
            }
            val arr = arr("studyTasks")
            arr.put(JSONObject()
                .put("id", System.currentTimeMillis())
                .put("goal", g)
                .put("subject", s)
                .put("title", t)
                .put("hours", num(hours, 1.0))
                .put("done", false)
                .put("created", today())
                .put("planDate", d)
                .put("doneAt", ""))
            saveArr("studyTasks", arr)
            renderStudy()
        })
        content.addView(card("添加任务", form))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val tasks = arr("studyTasks")
        if (tasks.length() == 0) {
            list.addView(label("还没有复习任务。先加几个大任务，比如数学一章、英语阅读、专业课一节。", 15f, false, textSub))
        }
        for (i in 0 until tasks.length()) {
            val o = tasks.getJSONObject(i)
            list.addView(taskRow(
                title = "${goalOf(o)} · ${o.optString("subject")} · ${o.optString("title")}",
                sub = "${planDateOf(o)} · ${one(o.optDouble("hours"))} h  ·  ${if (o.optBoolean("done")) "已完成" else "未完成"}",
                done = o.optBoolean("done"),
                onToggle = {
                    val nowDone = !o.optBoolean("done")
                    o.put("done", nowDone)
                    o.put("doneAt", if (nowDone) today() else "")
                    tasks.put(i, o)
                    saveArr("studyTasks", tasks)
                    renderStudy()
                },
                onDelete = {
                    tasks.remove(i)
                    saveArr("studyTasks", tasks)
                    renderStudy()
                }
            ))
        }
        content.addView(card("任务清单", list))
    }

    private fun renderFitness() {
        setScreen("fitness")
        val latest = latestWeight()
        val first = firstWeight()
        val avg7 = avgWeight7()
        val targetLoss = targetLossJin()
        val lostJin = if (latest > 0.0 && first > 0.0) (first - latest) * 2.0 else 0.0
        val title = if (latest > 0.0) "${one(latest)} kg" else "待记录"
        val desc = if (latest > 0.0) "已减 ${one(lostJin)} / ${one(targetLoss)} 斤；7 日平均 ${if (avg7 > 0) one(avg7) else "不足"} kg。" else "先记录一次当前体重。"
        content.addView(metricCard("当前体重", title, desc, amber))

        val lossInput = input("减重目标（斤）", one(targetLoss))
        lossInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val targetBox = LinearLayout(this)
        targetBox.orientation = LinearLayout.VERTICAL
        targetBox.addView(lossInput)
        targetBox.addView(action("保存减重目标") {
            prefs.edit().putFloat("targetLossJin", num(lossInput, 20.0).toFloat()).apply()
            renderFitness()
        })
        content.addView(card("目标设置", targetBox))

        val weightInput = input("今天体重 kg", "")
        weightInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val weightForm = LinearLayout(this)
        weightForm.orientation = LinearLayout.VERTICAL
        weightForm.addView(weightInput)
        weightForm.addView(action("保存体重") {
            val w = num(weightInput, -1.0)
            if (w <= 0.0) {
                toast("体重要填数字")
                return@action
            }
            val a = arr("weights")
            a.put(JSONObject().put("date", today()).put("weight", w))
            saveArr("weights", a)
            renderFitness()
        })
        content.addView(card("体重记录", weightForm))

        val type = input("运动类型，如 快走/力量/跑步", "快走")
        val minutes = input("运动时长 min", "30")
        minutes.inputType = InputType.TYPE_CLASS_NUMBER
        val workoutForm = LinearLayout(this)
        workoutForm.orientation = LinearLayout.VERTICAL
        workoutForm.addView(type)
        workoutForm.addView(minutes)
        workoutForm.addView(action("记录运动") {
            val m = num(minutes, 0.0).roundToInt()
            if (m <= 0) {
                toast("运动时长要大于 0")
                return@action
            }
            val a = arr("workouts")
            a.put(JSONObject().put("date", today()).put("type", type.text.toString().trim()).put("minutes", m))
            saveArr("workouts", a)
            renderFitness()
        })
        content.addView(card("运动记录", workoutForm))

        val recent = LinearLayout(this)
        recent.orientation = LinearLayout.VERTICAL
        val workouts = arr("workouts")
        val start = max(0, workouts.length() - 8)
        for (i in workouts.length() - 1 downTo start) {
            if (i < 0) break
            val o = workouts.getJSONObject(i)
            recent.addView(label("${o.optString("date")}  ${o.optString("type")}  ${o.optInt("minutes")} min", 15f, false, textMain))
        }
        if (workouts.length() == 0) recent.addView(label("还没有运动记录。考研期先按低门槛来：快走 20-40 分钟也算。", 15f, false, textSub))
        content.addView(card("最近运动", recent))
    }

    private fun renderBudget() {
        setScreen("budget")
        val currentCash = prefs.getFloat("currentCash", 0f).toDouble()
        val monthlyIncome = prefs.getFloat("monthlyIncome", 0f).toDouble()
        val fixedExpense = prefs.getFloat("fixedExpense", 0f).toDouble()
        val savingGoal = prefs.getFloat("savingGoal", 0f).toDouble()
        val manualBudget = prefs.getFloat("monthlyBudget", 1800f).toDouble()
        val autoBudget = monthlyIncome - fixedExpense - savingGoal
        val budget = if (monthlyIncome > 0.0) max(0.0, autoBudget) else manualBudget
        val spent = monthExpense()
        val surprise = monthSurpriseExpense()
        val remaining = budget - spent
        val daily = remaining / max(1, daysLeftInMonth())
        content.addView(metricCard("本月可用预算", "${one(remaining)} 元", "已花 ${one(spent)} / ${one(budget)} 元；意外开支 ${one(surprise)} 元；今日建议最多 ${one(daily)} 元。", if (remaining >= 0) green else red))

        val cashBox = LinearLayout(this)
        cashBox.orientation = LinearLayout.VERTICAL
        cashBox.addView(label("当前余额：${one(currentCash)} 元", 15f, false, textMain))
        if (monthlyIncome > 0.0) {
            cashBox.addView(label("月收入 ${one(monthlyIncome)} - 固定开支 ${one(fixedExpense)} - 存钱目标 ${one(savingGoal)} = 本月可变预算 ${one(budget)} 元", 14f, false, textSub))
        } else {
            cashBox.addView(label("还没设置收入结构，暂时使用手动预算 ${one(manualBudget)} 元。", 14f, false, textSub))
        }
        content.addView(card("资金概览", cashBox))

        val cashInput = input("现在手里/账户可用余额 元", one(currentCash))
        cashInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val incomeInput = input("本月收入 元，没有就填 0", one(monthlyIncome))
        incomeInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val fixedInput = input("本月固定开支 元，如房租/交通/话费", one(fixedExpense))
        fixedInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val saveInput = input("本月想存下 元", one(savingGoal))
        saveInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val budgetInput = input("手动可变预算 元，收入为 0 时使用", one(manualBudget))
        budgetInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val budgetBox = LinearLayout(this)
        budgetBox.orientation = LinearLayout.VERTICAL
        budgetBox.addView(cashInput)
        budgetBox.addView(incomeInput)
        budgetBox.addView(fixedInput)
        budgetBox.addView(saveInput)
        budgetBox.addView(budgetInput)
        budgetBox.addView(action("保存资金设置") {
            prefs.edit()
                .putFloat("currentCash", num(cashInput, 0.0).toFloat())
                .putFloat("monthlyIncome", num(incomeInput, 0.0).toFloat())
                .putFloat("fixedExpense", num(fixedInput, 0.0).toFloat())
                .putFloat("savingGoal", num(saveInput, 0.0).toFloat())
                .putFloat("monthlyBudget", num(budgetInput, 1800.0).toFloat())
                .apply()
            renderBudget()
        })
        content.addView(card("资金设置", budgetBox))

        val amount = input("金额 元", "")
        amount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val category = input("分类，如 吃饭/饮料零食/交通/资料", "吃饭")
        val surpriseFlag = input("是否意外开支：否 / 是", "否")
        val note = input("备注，可不填", "")
        val expenseForm = LinearLayout(this)
        expenseForm.orientation = LinearLayout.VERTICAL
        expenseForm.addView(amount)
        expenseForm.addView(category)
        expenseForm.addView(surpriseFlag)
        expenseForm.addView(note)
        expenseForm.addView(action("记录开支") {
            val money = num(amount, -1.0)
            if (money <= 0.0) {
                toast("金额要填数字")
                return@action
            }
            val a = arr("expenses")
            a.put(JSONObject()
                .put("date", today())
                .put("amount", money)
                .put("category", category.text.toString().trim())
                .put("surprise", surpriseFlag.text.toString().trim().contains("是"))
                .put("note", note.text.toString().trim()))
            saveArr("expenses", a)
            renderBudget()
        })
        content.addView(card("记一笔", expenseForm))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val expenses = arr("expenses")
        val start = max(0, expenses.length() - 10)
        for (i in expenses.length() - 1 downTo start) {
            if (i < 0) break
            val o = expenses.getJSONObject(i)
            val extra = if (o.optString("note").isNotEmpty()) " · ${o.optString("note")}" else ""
            val tag = if (o.optBoolean("surprise")) " · 意外" else ""
            list.addView(label("${o.optString("date")}  ${o.optString("category")}  ${one(o.optDouble("amount"))} 元$tag$extra", 15f, false, textMain))
        }
        if (expenses.length() == 0) list.addView(label("还没有开支记录。先从吃饭、饮料零食、交通这几类开始记。", 15f, false, textSub))
        content.addView(card("最近开支", list))
    }

    private fun renderTasks() {
        setScreen("tasks")
        val open = dailyTasksOpenCount()
        content.addView(metricCard("日常事务", "$open 个未完成", "学习任务不要和生活杂事混在一起，这里只放洗衣、报名、材料、采购这些事。", blue))

        val title = input("新增事务，如 洗衣服/报名确认/整理资料", "")
        val planDate = input("计划日期，例如 ${today()}", today())
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.addView(title)
        form.addView(planDate)
        form.addView(action("添加事务") {
            val t = title.text.toString().trim()
            if (t.isEmpty()) {
                toast("写一下要做什么")
                return@action
            }
            val d = planDate.text.toString().trim()
            runCatching { LocalDate.parse(d) }.onFailure {
                toast("日期格式要像 ${today()}")
                return@action
            }
            val a = arr("dailyTasks")
            a.put(JSONObject().put("id", System.currentTimeMillis()).put("title", t).put("done", false).put("created", today()).put("planDate", d))
            saveArr("dailyTasks", a)
            renderTasks()
        })
        content.addView(card("新增事务", form))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val tasks = arr("dailyTasks")
        if (tasks.length() == 0) {
            list.addView(label("还没有日常事务。把会打断学习的小事先丢进来。", 15f, false, textSub))
        }
        for (i in 0 until tasks.length()) {
            val o = tasks.getJSONObject(i)
            list.addView(taskRow(
                title = o.optString("title"),
                sub = "${planDateOf(o)} · ${if (o.optBoolean("done")) "已完成" else "未完成"}",
                done = o.optBoolean("done"),
                onToggle = {
                    o.put("done", !o.optBoolean("done"))
                    tasks.put(i, o)
                    saveArr("dailyTasks", tasks)
                    renderTasks()
                },
                onDelete = {
                    tasks.remove(i)
                    saveArr("dailyTasks", tasks)
                    renderTasks()
                }
            ))
        }
        content.addView(card("事务清单", list))
    }

    private fun renderReview() {
        setScreen("review")
        val stats = studyStats()
        val workoutMin = weekWorkoutMinutes()
        val workoutCount = weekWorkoutCount()
        val weekExpense = weekExpense()
        val budget = effectiveMonthlyBudget()
        val remaining = budget - monthExpense()

        content.addView(metricCard("本周复盘", statusWord(stats.weekDone, weeklyStudyGoal()), "学习 ${one(stats.weekDone)} h，运动 $workoutCount 次 / $workoutMin min，本周开支 ${one(weekExpense)} 元。", blue))

        val advice = LinearLayout(this)
        advice.orientation = LinearLayout.VERTICAL
        val lines = mutableListOf<String>()
        if (stats.weekDone < weeklyStudyGoal() * 0.7) {
            lines.add("复习：本周学习量低于目标 70%，下周先减少可选事务，每天补 1 个主科任务。")
        } else {
            lines.add("复习：学习量基本稳住了，下一步重点看薄弱科目，而不是只堆时长。")
        }
        if (workoutCount < 3) {
            lines.add("运动：本周运动少于 3 次，下周把快走 20 分钟作为最低保底。")
        } else {
            lines.add("运动：频率不错，考研期保持中低强度就可以。")
        }
        if (remaining < 0) {
            lines.add("开支：本月预算已经超了，优先砍饮料零食、外卖、娱乐。")
        } else {
            lines.add("开支：本月还剩 ${one(remaining)} 元，平均每天可用 ${one(remaining / max(1, daysLeftInMonth()))} 元。")
        }
        val latest = latestWeight()
        if (latest <= 0.0) {
            lines.add("体重：还没记录体重，建议每周固定 3 次早晨称重。")
        } else {
            lines.add("体重：不要盯单日波动，看 7 日平均更稳。")
        }
        lines.forEach { advice.addView(label("· $it", 15f, false, textMain)) }
        content.addView(card("调整建议", advice))
        content.addView(card("趋势统计", trendView()))
        content.addView(action("导出全部数据") {
            exportData()
        })
    }

    private fun agendaView(days: Int): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val studyTasks = arr("studyTasks")
        val dailyTasks = arr("dailyTasks")
        val start = LocalDate.now()
        var hasAny = false

        for (offset in 0 until days) {
            val date = start.plusDays(offset.toLong())
            val dayBox = LinearLayout(this)
            dayBox.orientation = LinearLayout.VERTICAL
            val items = mutableListOf<View>()

            for (i in 0 until studyTasks.length()) {
                val o = studyTasks.getJSONObject(i)
                if (parseDate(planDateOf(o)) == date) {
                    items.add(taskRow(
                        title = "${goalOf(o)} · ${o.optString("subject")} · ${o.optString("title")}",
                        sub = "${one(o.optDouble("hours"))} h · ${if (o.optBoolean("done")) "已完成" else "未完成"}",
                        done = o.optBoolean("done"),
                        onToggle = {
                            val nowDone = !o.optBoolean("done")
                            o.put("done", nowDone)
                            o.put("doneAt", if (nowDone) today() else "")
                            studyTasks.put(i, o)
                            saveArr("studyTasks", studyTasks)
                            refreshCurrent()
                        },
                        onDelete = {
                            studyTasks.remove(i)
                            saveArr("studyTasks", studyTasks)
                            refreshCurrent()
                        }
                    ))
                }
            }

            for (i in 0 until dailyTasks.length()) {
                val o = dailyTasks.getJSONObject(i)
                if (parseDate(planDateOf(o)) == date) {
                    items.add(taskRow(
                        title = o.optString("title"),
                        sub = if (o.optBoolean("done")) "已完成" else "未完成",
                        done = o.optBoolean("done"),
                        onToggle = {
                            o.put("done", !o.optBoolean("done"))
                            dailyTasks.put(i, o)
                            saveArr("dailyTasks", dailyTasks)
                            refreshCurrent()
                        },
                        onDelete = {
                            dailyTasks.remove(i)
                            saveArr("dailyTasks", dailyTasks)
                            refreshCurrent()
                        }
                    ))
                }
            }

            if (items.isNotEmpty()) {
                hasAny = true
                dayBox.addView(label(dayTitle(date, offset), 16f, true, textMain))
                items.forEach { dayBox.addView(it) }
                val lp = LinearLayout.LayoutParams(-1, -2)
                lp.setMargins(0, dp(8), 0, dp(8))
                box.addView(dayBox, lp)
            }
        }

        if (!hasAny) {
            box.addView(label("未来 7 天还没有安排。可以先把下周大任务粗略放进来，不用一开始排得很细。", 15f, false, textSub))
        }
        return box
    }

    private fun agendaOpenCount(days: Int): Int {
        val start = LocalDate.now()
        val end = start.plusDays((days - 1).toLong())
        return agendaCountBetween(start, end, onlyOpen = true)
    }

    private fun agendaCountFor(date: LocalDate, onlyOpen: Boolean): Int {
        return agendaCountBetween(date, date, onlyOpen)
    }

    private fun agendaCountBetween(start: LocalDate, end: LocalDate, onlyOpen: Boolean): Int {
        var count = 0
        val studyTasks = arr("studyTasks")
        val dailyTasks = arr("dailyTasks")
        for (i in 0 until studyTasks.length()) {
            val o = studyTasks.getJSONObject(i)
            val d = parseDate(planDateOf(o))
            if (d != null && !d.isBefore(start) && !d.isAfter(end) && (!onlyOpen || !o.optBoolean("done"))) count++
        }
        for (i in 0 until dailyTasks.length()) {
            val o = dailyTasks.getJSONObject(i)
            val d = parseDate(planDateOf(o))
            if (d != null && !d.isBefore(start) && !d.isAfter(end) && (!onlyOpen || !o.optBoolean("done"))) count++
        }
        return count
    }

    private fun planDateOf(o: JSONObject): String {
        val planDate = o.optString("planDate", "")
        if (planDate.isNotEmpty()) return planDate
        val created = o.optString("created", "")
        return created.ifEmpty { today() }
    }

    private fun parseDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun dayTitle(date: LocalDate, offset: Int): String {
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val prefix = when (offset) {
            0 -> "今天"
            1 -> "明天"
            else -> names[date.dayOfWeek.value - 1]
        }
        return "$prefix · $date"
    }

    private fun refreshCurrent() {
        when (currentScreen) {
            "dashboard" -> renderDashboard()
            "goals" -> renderGoals()
            "calendar" -> renderCalendar()
            "study" -> renderStudy()
            "fitness" -> renderFitness()
            "budget" -> renderBudget()
            "tasks" -> renderTasks()
            "review" -> renderReview()
            else -> renderDashboard()
        }
    }

    private fun goalsArr(): JSONArray {
        val raw = prefs.getString("goals", null)
        if (raw != null) return JSONArray(raw)
        val initial = JSONArray()
        initial.put(JSONObject()
            .put("id", System.currentTimeMillis())
            .put("name", prefs.getString("primaryGoalName", "考研") ?: "考研")
            .put("deadline", prefs.getString("examDate", "2026-12-20") ?: "2026-12-20")
            .put("weeklyHours", prefs.getFloat("weeklyStudyGoal", 42f).toDouble())
            .put("note", "默认目标，可删除或改成自己的 DDL"))
        saveArr("goals", initial)
        return initial
    }

    private fun nearestGoal(): JSONObject {
        val goals = goalsArr()
        var best: JSONObject? = null
        var bestDate: LocalDate? = null
        val today = LocalDate.now()
        for (i in 0 until goals.length()) {
            val g = goals.getJSONObject(i)
            val d = parseDate(g.optString("deadline"))
            if (d != null && !d.isBefore(today) && (bestDate == null || d.isBefore(bestDate))) {
                best = g
                bestDate = d
            }
        }
        if (best != null) return best!!
        return if (goals.length() > 0) goals.getJSONObject(0) else JSONObject().put("name", "目标").put("deadline", LocalDate.now().toString()).put("weeklyHours", 0.0)
    }

    private fun goalsOverviewView(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val goals = goalsArr()
        if (goals.length() == 0) {
            box.addView(label("还没有目标。去目标页添加 DDL 后，这里会显示最近几个目标。", 15f, false, textSub))
            return box
        }
        for (i in 0 until min(3, goals.length())) {
            val g = goals.getJSONObject(i)
            val d = parseDate(g.optString("deadline"))
            val days = if (d != null) ChronoUnit.DAYS.between(LocalDate.now(), d).toInt() else 0
            val stats = goalStudyStats(g.optString("name"))
            box.addView(label("${g.optString("name")} · D-${max(0, days)} · ${one(stats.doneHours)} / ${one(stats.totalHours)} h", 15f, false, textMain))
        }
        return box
    }

    private fun goalOf(o: JSONObject): String {
        val goal = o.optString("goal", "")
        if (goal.isNotEmpty()) return goal
        return nearestGoal().optString("name", "考研")
    }

    private fun targetLossJin(): Double = prefs.getFloat("targetLossJin", 20f).toDouble()

    private fun effectiveMonthlyBudget(): Double {
        val income = prefs.getFloat("monthlyIncome", 0f).toDouble()
        val fixed = prefs.getFloat("fixedExpense", 0f).toDouble()
        val saving = prefs.getFloat("savingGoal", 0f).toDouble()
        val manual = prefs.getFloat("monthlyBudget", 1800f).toDouble()
        return if (income > 0.0) max(0.0, income - fixed - saving) else manual
    }

    private fun monthSurpriseExpense(): Double {
        val now = LocalDate.now()
        val a = arr("expenses")
        var sum = 0.0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val d = parseDate(o.optString("date"))
            if (d != null && d.year == now.year && d.monthValue == now.monthValue && o.optBoolean("surprise")) {
                sum += o.optDouble("amount", 0.0)
            }
        }
        return sum
    }

    private fun trendView(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val today = LocalDate.now()
        for (offset in 6 downTo 0) {
            val d = today.minusDays(offset.toLong())
            val study = studyHoursOn(d)
            val expense = expenseOn(d)
            val workout = workoutMinutesOn(d)
            val weight = weightOn(d)
            val weightText = if (weight > 0.0) " · 体重 ${one(weight)} kg" else ""
            box.addView(label("$d · 学习 ${one(study)} h · 运动 ${workout} min · 开支 ${one(expense)} 元$weightText", 14f, false, textMain))
        }
        return box
    }

    private fun exportData() {
        val settings = JSONObject()
            .put("targetLossJin", targetLossJin())
            .put("weeklyStudyOverride", weeklyStudyOverride())
            .put("currentCash", prefs.getFloat("currentCash", 0f).toDouble())
            .put("monthlyIncome", prefs.getFloat("monthlyIncome", 0f).toDouble())
            .put("fixedExpense", prefs.getFloat("fixedExpense", 0f).toDouble())
            .put("savingGoal", prefs.getFloat("savingGoal", 0f).toDouble())
            .put("monthlyBudget", prefs.getFloat("monthlyBudget", 1800f).toDouble())
        val data = JSONObject()
            .put("exportedAt", today())
            .put("settings", settings)
            .put("goals", goalsArr())
            .put("studyTasks", arr("studyTasks"))
            .put("dailyTasks", arr("dailyTasks"))
            .put("weights", arr("weights"))
            .put("workouts", arr("workouts"))
            .put("expenses", arr("expenses"))
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, "ShorePilot 数据导出")
        intent.putExtra(Intent.EXTRA_TEXT, data.toString(2))
        startActivity(Intent.createChooser(intent, "导出 ShorePilot 数据"))
    }

    private fun studyStats(): StudyStats {
        val tasks = arr("studyTasks")
        var total = 0.0
        var done = 0.0
        var weekDone = 0.0
        val start = weekStart()
        val end = LocalDate.now()
        for (i in 0 until tasks.length()) {
            val o = tasks.getJSONObject(i)
            val h = o.optDouble("hours", 0.0)
            total += h
            if (o.optBoolean("done")) {
                done += h
                val doneAt = o.optString("doneAt", "")
                if (doneAt.isNotEmpty()) {
                    val d = runCatching { LocalDate.parse(doneAt) }.getOrNull()
                    if (d != null && !d.isBefore(start) && !d.isAfter(end)) weekDone += h
                }
            }
        }
        val percent = if (total > 0.0) done / total * 100.0 else 0.0
        val debt = max(0.0, weeklyStudyGoal() - weekDone)
        return StudyStats(total, done, percent, weekDone, debt)
    }

    private data class StudyStats(
        val totalHours: Double,
        val doneHours: Double,
        val percent: Double,
        val weekDone: Double,
        val debt: Double
    )

    private fun goalStudyStats(goalName: String): StudyStats {
        val tasks = arr("studyTasks")
        var total = 0.0
        var done = 0.0
        var weekDone = 0.0
        val start = weekStart()
        val end = LocalDate.now()
        for (i in 0 until tasks.length()) {
            val o = tasks.getJSONObject(i)
            if (goalOf(o) != goalName) continue
            val h = o.optDouble("hours", 0.0)
            total += h
            if (o.optBoolean("done")) {
                done += h
                val d = parseDate(o.optString("doneAt", ""))
                if (d != null && !d.isBefore(start) && !d.isAfter(end)) weekDone += h
            }
        }
        val percent = if (total > 0.0) done / total * 100.0 else 0.0
        val weekly = goalWeeklyHours(goalName)
        return StudyStats(total, done, percent, weekDone, max(0.0, weekly - weekDone))
    }

    private fun goalWeeklyHours(goalName: String): Double {
        val goals = goalsArr()
        for (i in 0 until goals.length()) {
            val g = goals.getJSONObject(i)
            if (g.optString("name") == goalName) return g.optDouble("weeklyHours", 0.0)
        }
        return 0.0
    }

    private fun weeklyStudyGoal(): Double {
        val override = weeklyStudyOverride()
        if (override > 0.0) return override
        val goals = goalsArr()
        var sum = 0.0
        for (i in 0 until goals.length()) sum += goals.getJSONObject(i).optDouble("weeklyHours", 0.0)
        return if (sum > 0.0) sum else 0.0
    }

    private fun weeklyStudyOverride(): Double = prefs.getFloat("weeklyStudyOverride", 0f).toDouble()

    private fun studyHoursOn(date: LocalDate): Double {
        val tasks = arr("studyTasks")
        var sum = 0.0
        for (i in 0 until tasks.length()) {
            val o = tasks.getJSONObject(i)
            val d = parseDate(o.optString("doneAt", ""))
            if (d == date && o.optBoolean("done")) sum += o.optDouble("hours", 0.0)
        }
        return sum
    }

    private fun expenseOn(date: LocalDate): Double {
        val expenses = arr("expenses")
        var sum = 0.0
        for (i in 0 until expenses.length()) {
            val o = expenses.getJSONObject(i)
            val d = parseDate(o.optString("date", ""))
            if (d == date) sum += o.optDouble("amount", 0.0)
        }
        return sum
    }

    private fun workoutMinutesOn(date: LocalDate): Int {
        val workouts = arr("workouts")
        var sum = 0
        for (i in 0 until workouts.length()) {
            val o = workouts.getJSONObject(i)
            val d = parseDate(o.optString("date", ""))
            if (d == date) sum += o.optInt("minutes", 0)
        }
        return sum
    }

    private fun weightOn(date: LocalDate): Double {
        val weights = arr("weights")
        var latest = 0.0
        for (i in 0 until weights.length()) {
            val o = weights.getJSONObject(i)
            val d = parseDate(o.optString("date", ""))
            if (d == date) latest = o.optDouble("weight", 0.0)
        }
        return latest
    }

    private fun firstWeight(): Double {
        val a = arr("weights")
        if (a.length() == 0) return 0.0
        return a.getJSONObject(0).optDouble("weight", 0.0)
    }

    private fun latestWeight(): Double {
        val a = arr("weights")
        if (a.length() == 0) return 0.0
        return a.getJSONObject(a.length() - 1).optDouble("weight", 0.0)
    }

    private fun avgWeight7(): Double {
        val a = arr("weights")
        if (a.length() == 0) return 0.0
        val start = max(0, a.length() - 7)
        var sum = 0.0
        var n = 0
        for (i in start until a.length()) {
            sum += a.getJSONObject(i).optDouble("weight", 0.0)
            n++
        }
        return if (n > 0) sum / n else 0.0
    }

    private fun monthExpense(): Double {
        val now = LocalDate.now()
        return expenseSum { d -> d.year == now.year && d.monthValue == now.monthValue }
    }

    private fun weekExpense(): Double {
        val start = weekStart()
        val end = LocalDate.now()
        return expenseSum { d -> !d.isBefore(start) && !d.isAfter(end) }
    }

    private fun expenseSum(filter: (LocalDate) -> Boolean): Double {
        val a = arr("expenses")
        var sum = 0.0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val d = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()
            if (d != null && filter(d)) sum += o.optDouble("amount", 0.0)
        }
        return sum
    }

    private fun weekWorkoutMinutes(): Int {
        val start = weekStart()
        val end = LocalDate.now()
        val a = arr("workouts")
        var sum = 0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val d = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()
            if (d != null && !d.isBefore(start) && !d.isAfter(end)) sum += o.optInt("minutes", 0)
        }
        return sum
    }

    private fun weekWorkoutCount(): Int {
        val start = weekStart()
        val end = LocalDate.now()
        val a = arr("workouts")
        var count = 0
        for (i in 0 until a.length()) {
            val d = runCatching { LocalDate.parse(a.getJSONObject(i).optString("date")) }.getOrNull()
            if (d != null && !d.isBefore(start) && !d.isAfter(end)) count++
        }
        return count
    }

    private fun dailyTasksOpenCount(): Int {
        val a = arr("dailyTasks")
        var n = 0
        for (i in 0 until a.length()) if (!a.getJSONObject(i).optBoolean("done")) n++
        return n
    }

    private fun weekStart(): LocalDate {
        val now = LocalDate.now()
        return now.minusDays((now.dayOfWeek.value - 1).toLong())
    }

    private fun daysLeftInMonth(): Int {
        val now = LocalDate.now()
        return now.lengthOfMonth() - now.dayOfMonth + 1
    }

    private fun arr(key: String): JSONArray = JSONArray(prefs.getString(key, "[]") ?: "[]")

    private fun saveArr(key: String, value: JSONArray) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    private fun today() = LocalDate.now().toString()

    private fun num(input: EditText, fallback: Double): Double {
        return input.text.toString().trim().toDoubleOrNull() ?: fallback
    }

    private fun statusWord(actual: Double, target: Double): String {
        return when {
            target <= 0.0 -> "未设置"
            actual >= target -> "达标"
            actual >= target * 0.7 -> "偏紧"
            else -> "危险"
        }
    }

    private fun one(v: Double): String {
        return if (kotlin.math.abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else String.format("%.1f", v)
    }

    private fun label(text: String, size: Float, bold: Boolean, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setLineSpacing(dp(2).toFloat(), 1.0f)
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        return t
    }

    private fun input(hint: String, value: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.textSize = 15f
        e.setSingleLine(false)
        e.setPadding(dp(10), dp(8), dp(10), dp(8))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(4), 0, dp(8))
        e.layoutParams = lp
        return e
    }

    private fun action(title: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = title
        b.textSize = 15f
        b.setTextColor(Color.WHITE)
        b.setBackgroundColor(blue)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, dp(46))
        lp.setMargins(0, dp(6), 0, 0)
        b.layoutParams = lp
        return b
    }

    private fun taskRow(title: String, sub: String, done: Boolean, onToggle: () -> Unit, onDelete: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(8), 0, dp(8))
        val titleColor = if (done) textSub else textMain
        box.addView(label(title, 15f, true, titleColor))
        box.addView(label(sub, 13f, false, textSub))
        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        val toggle = Button(this)
        toggle.text = if (done) "改为未完成" else "完成"
        toggle.setTextColor(Color.WHITE)
        toggle.setBackgroundColor(if (done) amber else green)
        toggle.setOnClickListener { onToggle() }
        val del = Button(this)
        del.text = "删除"
        del.setTextColor(Color.WHITE)
        del.setBackgroundColor(red)
        del.setOnClickListener { onDelete() }
        buttons.addView(toggle, weightLp())
        buttons.addView(del, weightLp())
        box.addView(buttons)
        return box
    }

    private fun metricCard(title: String, big: String, desc: String, color: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(label(title, 14f, true, textSub))
        box.addView(label(big, 33f, true, color))
        box.addView(label(desc, 14f, false, textMain))
        return card("", box)
    }

    private fun smallMetric(title: String, big: String, desc: String, color: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(12), dp(12), dp(12))
        box.setBackgroundColor(cardBg)
        box.addView(label(title, 13f, true, textSub))
        box.addView(label(big, 23f, true, color))
        box.addView(label(desc, 12f, false, textSub))
        return box
    }

    private fun card(title: String, inner: View): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(14), dp(16), dp(14))
        box.setBackgroundColor(cardBg)
        if (title.isNotEmpty()) {
            val h = label(title, 18f, true, textMain)
            val hp = LinearLayout.LayoutParams(-1, -2)
            hp.setMargins(0, 0, 0, dp(8))
            box.addView(h, hp)
        }
        box.addView(inner)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(8), 0, dp(8))
        box.layoutParams = lp
        return box
    }

    private fun weightLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        return lp
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
