package com.aivigil.periodtracker.profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aivigil.periodtracker.MainActivity
import com.aivigil.periodtracker.databinding.FragmentProfileBinding
import com.aivigil.periodtracker.notification.AlarmScheduler
import com.aivigil.periodtracker.notification.DailyLogReminderWorker
import com.aivigil.periodtracker.onboarding.OnboardingFragment5
import com.aivigil.periodtracker.profile.sheets.ActivitySheet
import com.aivigil.periodtracker.profile.sheets.AgeSheet
import com.aivigil.periodtracker.profile.sheets.ConditionsSheet
import com.aivigil.periodtracker.profile.sheets.CycleLengthSheet
import com.aivigil.periodtracker.profile.sheets.EditProfileSheet
import com.aivigil.periodtracker.profile.sheets.HeightSheet
import com.aivigil.periodtracker.profile.sheets.LastPeriodSheet
import com.aivigil.periodtracker.profile.sheets.PeriodDurationSheet
import com.aivigil.periodtracker.profile.sheets.WeightSheet
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwitches()
        observeData()
        bindClickListeners()
    }

    // ============================================================
    // UI SETUP
    // ============================================================

    // ✅ REMOVED setupAvatar() — the XML already uses @drawable/bg_avatar
    // for the gradient oval. Overriding it in code with a GradientDrawable
    // was redundant and ignored the drawable's corner radius/shape attributes.

    private fun setupSwitches() {
        val pink     = Color.parseColor("#EC4899")
        val pinkTrack = Color.parseColor("#FBCFE8")
        val offThumb = Color.parseColor("#D0C8D5")
        val offTrack = Color.parseColor("#EDE6F0")

        val thumbColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(pink, offThumb)
        )
        val trackColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(pinkTrack, offTrack)
        )

        listOf(binding.switchPeriod, binding.switchOvul, binding.switchDaily)
            .forEach { switch ->
                switch.thumbTintList = thumbColors
                switch.trackTintList = trackColors
            }
    }

    // ============================================================
    // OBSERVE DATA
    // ============================================================

    private fun observeData() {
        val dateFmt         = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        val memberSinceFmt  = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

        vm.settings.observe(viewLifecycleOwner) { s ->
            s ?: return@observe

            // ── Profile header ────────────────────────────────────
            binding.tvAvatar.text      = s.userName.firstOrNull()?.uppercase() ?: "S"
            binding.tvProfileName.text = s.userName
            binding.tvMemberSince.text = try {
                "Member since ${LocalDate.parse(s.memberSince).format(memberSinceFmt)}"
            } catch (_: Exception) { "Member since ${s.memberSince}" }

            // ── Cycle settings ────────────────────────────────────
            binding.tvCycleLengthValue.text   = "${s.cycleLength} days"
            binding.tvPeriodDurationValue.text = "${s.periodDuration} days"
            binding.tvLastPeriodValue.text = try {
                LocalDate.parse(s.lastPeriodStart).format(dateFmt)
            } catch (_: Exception) { s.lastPeriodStart }

            // ── Health profile ────────────────────────────────────
            binding.tvAgeValue.text      = "${s.age} yrs"
            binding.tvHeightValue.text   = "${s.heightCm} cm"
            binding.tvWeightValue.text   = "${s.weightKg.toInt()} kg"
            binding.tvActivityValue.text = s.activityLevel

            val condList = s.conditions.split(",").map { it.trim() }.filter { it.isNotBlank() }
            binding.tvConditionsValue.text = when {
                condList.isEmpty() -> "None"
                condList.size == 1 -> condList[0]
                else               -> "${condList.size} conditions"
            }
            binding.tvGoalValue.text = s.goal

            // ✅ FIX — tvCycleTypeBadge exists in XML but was never updated from
            // the fragment, so it always showed the hardcoded "Regular Cycle" text.
            // Now it's set from real cycle regularity data via vm.periodEntries.
        }

        // ✅ FIX — wire tvCycleTypeBadge to real regularity data
        vm.periodEntries.observe(viewLifecycleOwner) { entries ->
            val dates = entries.map {
                runCatching { LocalDate.parse(it.startDate) }.getOrNull()
            }.filterNotNull()
            val regularity = com.aivigil.periodtracker.domain.CycleEngine.cycleRegularity(dates)
            binding.tvCycleTypeBadge.text = regularity
        }
    }

    // ============================================================
    // CLICK LISTENERS
    // ============================================================

    private fun bindClickListeners() {

        // ── Profile edit ──────────────────────────────────────────
        binding.btnEdit.setOnClickListener { showEditProfileSheet() }

        // ── Cycle settings ────────────────────────────────────────
        binding.cardCycleLength.setOnClickListener   { showCycleLengthSheet() }
        binding.cardPeriodDuration.setOnClickListener { showPeriodDurationSheet() }
        binding.cardLastPeriod.setOnClickListener    { showLastPeriodSheet() }
        binding.btnEditLastPeriod.setOnClickListener { showLastPeriodSheet() }

        // ── Health profile ────────────────────────────────────────
        binding.rowAge.setOnClickListener        { showAgeSheet() }
        binding.rowHeight.setOnClickListener     { showHeightSheet() }
        binding.rowWeight.setOnClickListener     { showWeightSheet() }
        binding.rowActivity.setOnClickListener   { showActivitySheet() }
        binding.rowConditions.setOnClickListener { showConditionsSheet() }

        // ── Privacy ───────────────────────────────────────────────
        binding.rowDeleteData.setOnClickListener { showDeleteDialog() }

        // ── Reminders ─────────────────────────────────────────────
        val prefs = requireContext()
            .getSharedPreferences("reminder_prefs", android.content.Context.MODE_PRIVATE)

        binding.switchPeriod.isChecked = prefs.getBoolean("period_reminder", true)
        binding.switchOvul.isChecked   = prefs.getBoolean("ovulation_reminder", true)
        binding.switchDaily.isChecked  = prefs.getBoolean("daily_reminder", true)

        binding.switchPeriod.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("period_reminder", checked).apply()
            if (checked) {
                val date = vm.prediction.value?.nextPeriodDate
                if (date != null) {
                    Log.d("ProfileFragment", "Period alarm ON — scheduling for $date")
                    AlarmScheduler.schedulePeriodAlarms(requireContext(), date)
                } else {
                    Log.w("ProfileFragment", "Period alarm ON — no prediction yet, skipping")
                }
            } else {
                Log.d("ProfileFragment", "Period alarm OFF — cancelling")
                AlarmScheduler.cancelPeriodAlarms(requireContext())
            }
        }

        binding.switchOvul.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("ovulation_reminder", checked).apply()
            if (checked) {
                val date = vm.prediction.value?.ovulationDate
                if (date != null) {
                    Log.d("ProfileFragment", "Ovulation alarm ON — scheduling for $date")
                    AlarmScheduler.scheduleOvulationAlarm(requireContext(), date)
                } else {
                    Log.w("ProfileFragment", "Ovulation alarm ON — no prediction yet, skipping")
                }
            } else {
                Log.d("ProfileFragment", "Ovulation alarm OFF — cancelling")
                AlarmScheduler.cancelOvulationAlarm(requireContext())
            }
        }

        binding.switchDaily.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("daily_reminder", checked).apply()
            if (checked) {
                Log.d("ProfileFragment", "Daily reminder ON — scheduling WorkManager")
                val request = PeriodicWorkRequestBuilder<DailyLogReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(calculateDelayUntil8pm(), TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                    "daily_log_reminder",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } else {
                Log.d("ProfileFragment", "Daily reminder OFF — cancelling WorkManager")
                WorkManager.getInstance(requireContext())
                    .cancelUniqueWork("daily_log_reminder")
            }
        }
    }

    // ============================================================
    // SHEETS
    // ============================================================

    private fun showEditProfileSheet() {
        val s = vm.settings.value ?: return
        EditProfileSheet(
            currentName     = s.userName,
            currentAge      = s.age,
            currentHeightCm = s.heightCm,
            currentWeightKg = s.weightKg
        ) { name, age, heightCm, weightKg ->
            vm.updateProfile(name, age, heightCm, weightKg)
        }.show(childFragmentManager, EditProfileSheet.TAG)
    }

    private fun showCycleLengthSheet() {
        CycleLengthSheet(vm.settings.value?.cycleLength ?: 28) { newLength ->
            vm.updateCycleLength(newLength)
        }.show(childFragmentManager, CycleLengthSheet.TAG)
    }

    private fun showPeriodDurationSheet() {
        PeriodDurationSheet(vm.settings.value?.periodDuration ?: 5) { newDuration ->
            vm.updatePeriodDuration(newDuration)
        }.show(childFragmentManager, PeriodDurationSheet.TAG)
    }

    private fun showLastPeriodSheet() {
        val current = vm.settings.value?.lastPeriodStart
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        LastPeriodSheet(current) { newDate ->
            vm.updateLastPeriodStart(newDate)
        }.show(childFragmentManager, LastPeriodSheet.TAG)
    }

    private fun showAgeSheet() {
        AgeSheet(vm.settings.value?.age ?: 25) { newAge ->
            val s = vm.settings.value ?: return@AgeSheet
            vm.updateProfile(s.userName, newAge, s.heightCm, s.weightKg)
        }.show(childFragmentManager, AgeSheet.TAG)
    }

    private fun showHeightSheet() {
        HeightSheet(vm.settings.value?.heightCm ?: 160) { newHeight ->
            val s = vm.settings.value ?: return@HeightSheet
            vm.updateProfile(s.userName, s.age, newHeight, s.weightKg)
        }.show(childFragmentManager, HeightSheet.TAG)
    }

    private fun showWeightSheet() {
        WeightSheet(vm.settings.value?.weightKg ?: 60f) { newWeight ->
            val s = vm.settings.value ?: return@WeightSheet
            vm.updateProfile(s.userName, s.age, s.heightCm, newWeight)
        }.show(childFragmentManager, WeightSheet.TAG)
    }

    private fun showConditionsSheet() {
        ConditionsSheet(
            vm.allLogs.value ?: emptyList(),
            vm.settings.value?.conditions ?: ""
        ).show(childFragmentManager, ConditionsSheet.TAG)
    }

    private fun showActivitySheet() {
        val current = vm.settings.value?.activityLevel ?: "BALANCED"
        val level = try {
            OnboardingFragment5.ActivityLevel.valueOf(current)
        } catch (_: Exception) {
            OnboardingFragment5.ActivityLevel.BALANCED
        }
        ActivitySheet(level) { newLevel ->
            vm.updateActivityLevel(newLevel.name)
        }.show(childFragmentManager, ActivitySheet.TAG)
    }

    // ============================================================
    // DELETE DATA
    // ============================================================

    private fun showDeleteDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete all data?")
            .setMessage(
                "This will permanently erase your cycle history, " +
                        "daily logs, profile information, and settings."
            )
            .setPositiveButton("Delete") { _, _ ->
                vm.deleteAllData()
                requireActivity().finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private fun calculateDelayUntil8pm(): Long {
        val now    = java.time.LocalDateTime.now()
        var target = now.withHour(20).withMinute(0).withSecond(0)
        if (now.isAfter(target)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}