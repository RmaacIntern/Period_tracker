package com.aivigil.periodtracker.profile

import com.aivigil.periodtracker.R

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aivigil.periodtracker.ads.NativeAdHelper
import com.aivigil.periodtracker.databinding.FragmentProfileBinding
import com.aivigil.periodtracker.notification.AlarmScheduler
import com.aivigil.periodtracker.notification.NotificationPrefs
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

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var nativeAdHelper: NativeAdHelper? = null

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
        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)
    }

    // ============================================================
    // UI SETUP
    // ============================================================

    // ✅ REMOVED setupAvatar() — the XML already uses @drawable/bg_avatar
    // for the gradient oval. Overriding it in code with a GradientDrawable
    // was redundant and ignored the drawable's corner radius/shape attributes.

    private fun setupSwitches() {
        val pink     = Color.parseColor("#EC4899")
        val pinkTrack = requireContext().getColor(R.color.stroke_pink)
        val offThumb = requireContext().getColor(R.color.switch_off_thumb)
        val offTrack = requireContext().getColor(R.color.divider)

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

            val condList = s.conditions.split(",").map { it.trim() }.filter { it.isNotBlank() }
            binding.tvConditionsValue.text = when {
                condList.isEmpty() -> "None"
                condList.size == 1 -> condList[0]
                else               -> "${condList.size} conditions"
            }
            // FIX: this printed the raw stored value, so the user saw the enum
            // name "TRACK_CYCLE" / "BALANCED" instead of a readable label.
            binding.tvGoalValue.text     = prettyLabel(s.goal)
            binding.tvActivityValue.text = prettyLabel(s.activityLevel)

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
        //
        // FIX: these switches wrote to SharedPreferences and cancelled their
        // alarms, but nothing else read those keys. CycleViewModel.refresh()
        // re-armed period and ovulation alarms on every DB write, and
        // MainActivity re-enqueued the daily worker on every launch — so turning
        // a reminder off only held until the next app launch. All scheduling now
        // goes through NotificationPrefs, which reads these preferences.
        val ctx = requireContext()

        binding.switchPeriod.isChecked = NotificationPrefs.isPeriodEnabled(ctx)
        binding.switchOvul.isChecked   = NotificationPrefs.isOvulationEnabled(ctx)
        binding.switchDaily.isChecked  = NotificationPrefs.isDailyEnabled(ctx)

        binding.switchPeriod.setOnCheckedChangeListener { _, checked ->
            NotificationPrefs.setPeriodEnabled(ctx, checked)
            if (checked) {
                checkExactAlarmAndWarn(ctx)
            }
            applyReminderState()
            if (checked) warnIfNotificationsBlocked()
        }

        binding.switchOvul.setOnCheckedChangeListener { _, checked ->
            NotificationPrefs.setOvulationEnabled(ctx, checked)
            if (checked) {
                checkExactAlarmAndWarn(ctx)
            }
            applyReminderState()
            if (checked) warnIfNotificationsBlocked()
        }

        binding.switchDaily.setOnCheckedChangeListener { _, checked ->
            NotificationPrefs.setDailyEnabled(ctx, checked)
            if (checked) {
                checkExactAlarmAndWarn(ctx)
            }
            NotificationPrefs.syncDailyReminder(ctx)
            if (checked) warnIfNotificationsBlocked()
        }

    }
    /**
     * If the OS has not granted exact-alarm permission, open the system
     * settings screen so the user can enable it. Without this, alarms
     * fire late (inexact fallback) and the user never knows why.
     */
    private fun checkExactAlarmAndWarn(ctx: android.content.Context) {
        if (com.aivigil.periodtracker.notification.ExactAlarmPermissionHelper
                .canScheduleExact(ctx)) return
        val root = _binding?.root ?: return
        com.google.android.material.snackbar.Snackbar
            .make(root, "Allow exact alarms for on-time reminders", 7000)
            .setAction("Enable") {
                com.aivigil.periodtracker.notification.ExactAlarmPermissionHelper
                    .openExactAlarmSettings(ctx)
            }
            .show()
    }

    /** Re-arms period/ovulation alarms from the current prediction and prefs. */
    private fun applyReminderState() {
        val pred = vm.prediction.value
        if (pred == null) {
            // No prediction yet — cancel so nothing stale survives. The alarms
            // will be armed by CycleViewModel as soon as a prediction exists.
            AlarmScheduler.cancelPeriodAlarms(requireContext())
            AlarmScheduler.cancelOvulationAlarm(requireContext())
            Log.d("ProfileFragment", "applyReminderState: no prediction yet")
            return
        }
        NotificationPrefs.rescheduleFromPrediction(
            context    = requireContext(),
            nextPeriod = pred.nextPeriodDate,
            ovulation  = pred.ovulationDate
        )
    }

    /**
     * A switch turned ON while the OS is blocking notifications would do nothing
     * with no explanation. Tell the user and offer the system settings screen
     * rather than leaving a reminder that looks enabled but never fires.
     */
    private fun warnIfNotificationsBlocked() {
        if (NotificationPrefs.canPostNotifications(requireContext())) return
        val root = _binding?.root ?: return
        com.google.android.material.snackbar.Snackbar
            .make(root, "Notifications are turned off for this app", 6000)
            .setAction("Settings") {
                runCatching {
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).putExtra("android.provider.extra.APP_PACKAGE", requireContext().packageName)
                    )
                }
            }
            .show()
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

    // calculateDelayUntil8pm() moved to NotificationPrefs.millisUntilHour() so the
    // daily-reminder schedule is computed in exactly one place.

    /**
     * Turns a stored enum-style value into something readable.
     * "TRACK_CYCLE" → "Track Cycle", "VERY_ACTIVE" → "Very Active".
     * Values already written in prose are returned untouched.
     */
    private fun prettyLabel(raw: String): String {
        if (raw.isBlank()) return "—"
        if (!raw.contains('_') && raw != raw.uppercase()) return raw
        return raw.split('_', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}